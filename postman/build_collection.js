#!/usr/bin/env node
/**
 * CoVehicle Postman collection generator.
 * Run:  node postman/build_collection.js
 * Outputs:
 *   postman/CoVehicle.postman_collection.json
 *   postman/CoVehicle.local.postman_environment.json
 */

'use strict';
const fs   = require('fs');
const path = require('path');

// ─── helpers ────────────────────────────────────────────────────────────────

function uid() {
  return Math.random().toString(36).slice(2, 10) +
         Math.random().toString(36).slice(2, 10);
}

function req(name, method, url, body, tests, description, prereq) {
  const item = {
    name,
    request: {
      method: method.toUpperCase(),
      header: [{ key: 'Content-Type', value: 'application/json' }],
      url: { raw: url, ...parseUrl(url) }
    },
    event: [],
    response: []
  };
  if (description) item.request.description = description;
  if (body) {
    item.request.body = { mode: 'raw', raw: JSON.stringify(body, null, 2),
      options: { raw: { language: 'json' } } };
  }
  if (prereq) {
    item.event.push({ listen: 'prerequest', script: { type: 'text/javascript',
      exec: Array.isArray(prereq) ? prereq : prereq.split('\n') } });
  }
  if (tests) {
    item.event.push({ listen: 'test', script: { type: 'text/javascript',
      exec: Array.isArray(tests) ? tests : tests.split('\n') } });
  }
  item._postman_id = uid();
  return item;
}

function folder(name, items, description) {
  return { name, item: items, description: description || '',
           _postman_id: uid() };
}

function parseUrl(raw) {
  // Split off query string manually (before any URL parsing)
  const qIdx = raw.indexOf('?');
  const pathPart = qIdx === -1 ? raw : raw.slice(0, qIdx);
  const queryStr = qIdx === -1 ? '' : raw.slice(qIdx + 1);

  // Parse query params manually (handles {{vars}} in values)
  const query = [];
  if (queryStr) {
    for (const pair of queryStr.split('&')) {
      const eqIdx = pair.indexOf('=');
      if (eqIdx === -1) {
        query.push({ key: decodeURIComponent(pair), value: '' });
      } else {
        query.push({
          key: decodeURIComponent(pair.slice(0, eqIdx)),
          value: decodeURIComponent(pair.slice(eqIdx + 1))
        });
      }
    }
  }

  // Parse protocol, host, path from the path portion
  // Handles {{baseUrl}}/some/path  OR  http://host/some/path
  const protoMatch = pathPart.match(/^([a-zA-Z][a-zA-Z0-9+\-.]*):\/\//);
  let protocol = null;
  let rest = pathPart;
  if (protoMatch) {
    protocol = protoMatch[1];
    rest = pathPart.slice(protoMatch[0].length);
  }

  // rest is now  host/path  or  {{baseUrl}}/path
  const slashIdx = rest.indexOf('/');
  const hostRaw = slashIdx === -1 ? rest : rest.slice(0, slashIdx);
  const pathRaw = slashIdx === -1 ? '' : rest.slice(slashIdx + 1);

  // host: split by '.' but keep {{vars}} as single tokens
  const host = hostRaw
    ? hostRaw.split('.').map(h => h)
    : [];

  // path: split by '/' keeping {{vars}} intact
  const path = pathRaw
    ? pathRaw.split('/').filter(Boolean)
    : [];

  // Only include protocol when it was explicitly in the raw URL
  // (if host is a {{variable}}, the variable itself carries the protocol)
  const result = { raw, host, path };
  if (protocol) result.protocol = protocol;
  if (query.length) result.query = query;
  return result;
}

// ─── collection-level pre-request script ────────────────────────────────────

const COLLECTION_PREREQ = [
  "// Compute day5 = today + 5 days as yyyy-MM-dd",
  "const d = new Date();",
  "d.setDate(d.getDate() + 5);",
  "const pad = n => String(n).padStart(2,'0');",
  "const day5 = d.getFullYear() + '-' + pad(d.getMonth()+1) + '-' + pad(d.getDate());",
  "pm.collectionVariables.set('day5', day5);"
];

// ─── collection-level test script (ProblemDetail guard) ─────────────────────
// NOTE on app behaviour:
//   - Custom exceptions (BadRequestException, ConflictException, etc.) → full ProblemDetail
//     with type=urn:covehicle:error:*, detail, instance, timestamp.
//   - Bean-validation errors (MethodArgumentNotValidException) → same + fieldErrors.
//   - Spring MVC infrastructure errors (malformed JSON body, missing/bad request params)
//     → also wrapped in ProblemDetail by GlobalExceptionHandler overrides.
//   The collection-level test asserts ProblemDetail shape for EVERY 4xx/5xx response,
//   and additionally checks fieldErrors for bean-validation 400s.

const COLLECTION_TEST = [
  "const status = pm.response.code;",
  "if (status >= 400) {",
  "    let body;",
  "    try { body = pm.response.json(); } catch(e) { return; }",
  "    pm.test('Error response is ProblemDetail', function () {",
  "        pm.expect(body).to.have.property('timestamp');",
  "        pm.expect(body.status).to.eql(status);",
  "        pm.expect(body.type).to.match(/^urn:covehicle:error:/);",
  "        pm.expect(body).to.have.property('detail');",
  "        pm.expect(body.instance).to.eql(pm.request.url.getPath());",
  "    });",
  "    if (status === 400 && body.fieldErrors) {",
  "        pm.test('Validation error has fieldErrors', function () {",
  "            pm.expect(body.fieldErrors).to.be.an('object');",
  "        });",
  "    }",
  "}"
];

// ─── reusable stop body builder ──────────────────────────────────────────────

function stopBody(city, time, price) {
  return { city, arrivalTime: `{{day5}}T${time}:00`, priceFromStart: price };
}

// ─── FOLDER 00 – Setup ───────────────────────────────────────────────────────

const setup00 = folder('00 Setup', [

  req(
    'Create second vehicle for Driver 1 (for second trip)',
    'POST',
    '{{baseUrl}}/api/drivers/1/vehicles',
    { brand: 'Renault', model: 'Megane', color: 'Blue',
      licensePlate: 'SEC-{{$timestamp}}', capacity: 4 },
    [
      "pm.test('Status 201', () => pm.response.to.have.status(201));",
      "pm.collectionVariables.set('secondVehicleId', pm.response.json().id);"
    ],
    'Creates a second vehicle so we can publish a second trip on the same day without double-booking.'
  ),

  req(
    'Create Setup Trip (Hamid, Rabat→Marrakech)',
    'POST',
    '{{baseUrl}}/api/drivers/1/trips',
    {
      vehicleId: 1,
      seatsOffered: 3,
      stops: [
        stopBody('Rabat',       '17:00', 0),
        stopBody('Casablanca',  '18:15', 40),
        stopBody('Settat',      '19:15', 70),
        stopBody('Marrakech',   '21:30', 120)
      ]
    },
    [
      "pm.test('Status 201', () => pm.response.to.have.status(201));",
      "const body = pm.response.json();",
      "pm.test('Status PUBLISHED', () => pm.expect(body.status).to.eql('PUBLISHED'));",
      "pm.test('remainingSeats 3', () => pm.expect(body.remainingSeats).to.eql(3));",
      "pm.test('4 stops', () => pm.expect(body.stops).to.have.lengthOf(4));",
      "pm.test('stopOrder 0..3', () => {",
      "    const orders = body.stops.map(s => s.stopOrder).sort((a,b)=>a-b);",
      "    pm.expect(orders).to.eql([0,1,2,3]);",
      "});",
      "pm.test('departureCity Rabat', () => pm.expect(body.departureCity).to.eql('Rabat'));",
      "pm.test('arrivalCity Marrakech', () => pm.expect(body.arrivalCity).to.eql('Marrakech'));",
      "pm.collectionVariables.set('tripId', body.id);",
      "const byCity = c => body.stops.find(s => s.city === c).id;",
      "pm.collectionVariables.set('stopRabat',     byCity('Rabat'));",
      "pm.collectionVariables.set('stopCasa',      byCity('Casablanca'));",
      "pm.collectionVariables.set('stopSettat',    byCity('Settat'));",
      "pm.collectionVariables.set('stopMarrakech', byCity('Marrakech'));"
    ],
    'Creates the main test trip. Saves tripId and stop ids as collection variables.'
  ),

  req(
    'GET Setup Trip',
    'GET',
    '{{baseUrl}}/api/trips/{{tripId}}',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const body = pm.response.json();",
      "pm.test('remainingSeats 3', () => pm.expect(body.remainingSeats).to.eql(3));",
      "pm.test('stops in order', () => {",
      "    for (let i = 0; i < body.stops.length - 1; i++) {",
      "        pm.expect(body.stops[i].stopOrder).to.be.lessThan(body.stops[i+1].stopOrder);",
      "    }",
      "});"
    ],
    'Verifies the created trip is retrievable with correct data.'
  ),

  req(
    'Create second trip (Settat→Marrakech, same day)',
    'POST',
    '{{baseUrl}}/api/drivers/1/trips',
    {
      vehicleId: '{{secondVehicleId}}',
      seatsOffered: 3,
      stops: [
        { city: 'Settat',      arrivalTime: `{{day5}}T18:00:00`, priceFromStart: 0 },
        { city: 'Marrakech',   arrivalTime: `{{day5}}T20:00:00`, priceFromStart: 45 }
      ]
    },
    [
      "pm.test('Status 201', () => pm.response.to.have.status(201));",
      "const body = pm.response.json();",
      "pm.test('Status PUBLISHED', () => pm.expect(body.status).to.eql('PUBLISHED'));",
      "pm.collectionVariables.set('secondTripId', body.id);"
    ],
    'Creates a second published trip on the same day for sorting tests.'
  ),

  req(
    'Search Settat→Marrakech sorted by departureDate asc',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day5}}',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const body = pm.response.json();",
      "const content = body.content;",
      "pm.test('at least 2 results', () => pm.expect(content.length).to.be.at.least(2));",
      "pm.test('sorted by departureDate ascending', () => {",
      "    for (let i = 1; i < content.length; i++) {",
      "        pm.expect(new Date(content[i].departureDate).getTime()).to.be.at.least(new Date(content[i-1].departureDate).getTime());",
      "    }",
      "});",
      "pm.test('setup tripId present', () => {",
      "    const tripId = pm.collectionVariables.get('tripId');",
      "    pm.expect(content.some(t => String(t.tripId) === String(tripId))).to.be.true;",
      "});",
      "pm.test('second tripId present', () => {",
      "    const secondTripId = pm.collectionVariables.get('secondTripId');",
      "    pm.expect(content.some(t => String(t.tripId) === String(secondTripId))).to.be.true;",
      "});"
    ],
    'Verifies results are sorted by departureDate ascending and both trips are present.'
  ),

  req(
    'Search Settat→Marrakech size=1 totalPages >= 2',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day5}}&size=1',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const body = pm.response.json();",
      "pm.test('totalPages >= 2', () => pm.expect(body.totalPages).to.be.at.least(2));",
      "pm.test('has tripId', () => pm.expect(body.content[0]).to.have.property('tripId'));"
    ],
    'Verifies pagination with size=1 returns totalPages >= 2 since we have 2 trips.'
  ),

  req(
    'Search Settat→Marrakech (positive)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day5}}',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const body = pm.response.json();",
      "const tripId = pm.collectionVariables.get('tripId');",
      "const found = body.content.find(t => String(t.tripId) === String(tripId));",
      "pm.test('tripId present in results', () => pm.expect(found).to.not.be.undefined);",
      "pm.test('segmentPrice 50', () => pm.expect(Number(found.segmentPrice)).to.eql(50));",
      "pm.test('remainingSeats 3', () => pm.expect(Number(found.remainingSeats)).to.eql(3));"
    ],
    'Positive search: segment Settat→Marrakech costs 120-70=50.'
  )

], 'Creates the main trip used by all subsequent scenarios. Must run first.');

// ─── FOLDER 01 – Brief Scenarios ─────────────────────────────────────────────

const scenarios01 = folder('01 Brief Scenarios', [

  req(
    'S1 - Salma books 1 seat Settat to Marrakech',
    'POST',
    '{{baseUrl}}/api/passengers/6/reservations',
    { tripId: '{{tripId}}', pickupStopId: '{{stopSettat}}',
      dropoffStopId: '{{stopMarrakech}}', numberOfSeats: 1 },
    [
      "pm.test('Status 201', () => pm.response.to.have.status(201));",
      "const body = pm.response.json();",
      "pm.test('totalAmount 50.00', () => pm.expect(Number(body.totalAmount)).to.eql(50));",
      "pm.test('remainingSeats 2', () => pm.expect(body.remainingSeats).to.eql(2));",
      "pm.test('status CONFIRMED', () => pm.expect(body.status).to.eql('CONFIRMED'));",
      "pm.collectionVariables.set('salmaReservationId', body.id);"
    ],
    'Rule: passenger can book available seats on a PUBLISHED trip.'
  ),

  req(
    'S2 - Search Settat→Casablanca (no results for setup trip)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Casablanca&date={{day5}}',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const body = pm.response.json();",
      "const tripId = pm.collectionVariables.get('tripId');",
      "const found = body.content.find(t => String(t.tripId) === String(tripId));",
      "pm.test('setup tripId NOT in results (Casablanca is before Settat)', () => pm.expect(found).to.be.undefined);"
    ],
    'Rule: search only returns trips where pickup stop comes before dropoff stop.'
  ),

  req(
    'S3 - Karim books 2 seats Casablanca to Marrakech',
    'POST',
    '{{baseUrl}}/api/passengers/7/reservations',
    { tripId: '{{tripId}}', pickupStopId: '{{stopCasa}}',
      dropoffStopId: '{{stopMarrakech}}', numberOfSeats: 2 },
    [
      "pm.test('Status 201', () => pm.response.to.have.status(201));",
      "const body = pm.response.json();",
      "pm.test('totalAmount 160.00', () => pm.expect(Number(body.totalAmount)).to.eql(160));",
      "pm.test('remainingSeats 0', () => pm.expect(body.remainingSeats).to.eql(0));",
      "pm.test('tripStatus FULL', () => pm.expect(body.tripStatus).to.eql('FULL'));",
      "pm.collectionVariables.set('karimReservationId', body.id);"
    ],
    'Rule: booking 2 seats (80 each) fills the trip → FULL.'
  ),

  req(
    'S4 - Imane tries to book on FULL trip (409)',
    'POST',
    '{{baseUrl}}/api/passengers/8/reservations',
    { tripId: '{{tripId}}', pickupStopId: '{{stopSettat}}',
      dropoffStopId: '{{stopMarrakech}}', numberOfSeats: 1 },
    [
      "pm.test('Status 409', () => pm.response.to.have.status(409));"
    ],
    'Rule: cannot book on a FULL trip.'
  ),

  req(
    'S5a - Karim cancels his reservation',
    'DELETE',
    '{{baseUrl}}/api/passengers/7/reservations/{{karimReservationId}}',
    null,
    [
      "pm.test('Status 204', () => pm.response.to.have.status(204));"
    ],
    'Rule: passenger can cancel their own reservation.'
  ),

  req(
    'S5b - GET trip after Karim cancels (PUBLISHED, 2 seats)',
    'GET',
    '{{baseUrl}}/api/trips/{{tripId}}',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const body = pm.response.json();",
      "pm.test('status PUBLISHED', () => pm.expect(body.status).to.eql('PUBLISHED'));",
      "pm.test('remainingSeats 2', () => pm.expect(body.remainingSeats).to.eql(2));"
    ],
    'Rule: cancelling a reservation restores seats and reverts FULL→PUBLISHED.'
  ),

  req(
    'S6 - Imane books reversed stops (pickup after dropoff → 400)',
    'POST',
    '{{baseUrl}}/api/passengers/8/reservations',
    { tripId: '{{tripId}}', pickupStopId: '{{stopMarrakech}}',
      dropoffStopId: '{{stopSettat}}', numberOfSeats: 1 },
    [
      "pm.test('Status 400', () => pm.response.to.have.status(400));"
    ],
    'Rule: pickup stop must come before dropoff stop in trip order.'
  ),

  req(
    'S7 - Create trip seatsOffered > vehicle capacity (400)',
    'POST',
    '{{baseUrl}}/api/drivers/1/trips',
    {
      vehicleId: 1,
      seatsOffered: 5,
      stops: [
        stopBody('Rabat',      '17:00', 0),
        stopBody('Casablanca', '18:15', 40),
        stopBody('Settat',     '19:15', 70),
        stopBody('Marrakech',  '21:30', 120)
      ]
    },
    [
      "pm.test('Status 400', () => pm.response.to.have.status(400));",
      "pm.test('detail mentions 4 places au maximum', () => {",
      "    const body = pm.response.json();",
      "    pm.expect(body.detail).to.include('4 places au maximum');",
      "});"
    ],
    'Rule: seatsOffered cannot exceed vehicle capacity (Dacia Logan capacity=4).'
  ),

  req(
    'S8 - Hamid creates trip with Youssef\'s vehicle (403)',
    'POST',
    '{{baseUrl}}/api/drivers/1/trips',
    {
      vehicleId: 2,
      seatsOffered: 3,
      stops: [
        stopBody('Rabat',      '17:00', 0),
        stopBody('Casablanca', '18:15', 40),
        stopBody('Settat',     '19:15', 70),
        stopBody('Marrakech',  '21:30', 120)
      ]
    },
    [
      "pm.test('Status 403', () => pm.response.to.have.status(403));"
    ],
    'Rule: driver cannot use a vehicle that belongs to another driver.'
  ),

  req(
    'S9 - Complete trip departing in 5 days (409)',
    'POST',
    '{{baseUrl}}/api/drivers/1/trips/{{tripId}}/complete',
    null,
    [
      "pm.test('Status 409', () => pm.response.to.have.status(409));"
    ],
    'Rule: cannot complete a trip that has not yet departed.'
  ),

  req(
    'S10 - Nadia cancels reservation < 24h before departure (409)',
    'DELETE',
    '{{baseUrl}}/api/passengers/10/reservations/6',
    null,
    [
      "pm.test('Status 409', () => pm.response.to.have.status(409));",
      "pm.test('detail mentions 24 hours rule', () => {",
      "    const body = pm.response.json();",
      "    pm.expect(body.detail.toLowerCase()).to.satisfy(d =>",
      "        d.includes('24') || d.includes('heure') || d.includes('hour'));",
      "});"
    ],
    'Rule: cannot cancel a reservation less than 24 hours before departure. Depends on seed (trip 5 departs in ~2h).'
  ),

  req(
    'S11a - Youssef cancels seed trip 2 (has 2 confirmed reservations)',
    'POST',
    '{{baseUrl}}/api/drivers/2/trips/2/cancel',
    null,
    [
      "pm.test('Status 204', () => pm.response.to.have.status(204));"
    ],
    'Rule: driver can cancel a FULL trip; all confirmed reservations become CANCELED. NOT rerunnable without reseeding.'
  ),

  req(
    'S11b - Salma\'s reservation on trip 2 is now CANCELED',
    'GET',
    '{{baseUrl}}/api/passengers/6/reservations',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const body = pm.response.json();",
      "const r = body.find(r => r.tripId === 2);",
      "pm.test('Salma reservation on trip 2 is CANCELED', () => pm.expect(r.status).to.eql('CANCELED'));"
    ],
    'Verifies cascade cancellation of passenger reservations when driver cancels trip.'
  ),

  req(
    'S11c - Trip 2 status is CANCELED',
    'GET',
    '{{baseUrl}}/api/trips/2',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "pm.test('Trip 2 CANCELED', () => pm.expect(pm.response.json().status).to.eql('CANCELED'));"
    ],
    'Verifies trip status after driver cancellation.'
  )

], 'Exactly the brief\'s scenario table in order. S10 and S11 depend on seed data. To rerun S11 the database must be reseeded.');

// ─── FOLDER 02 – Profiles ────────────────────────────────────────────────────

const profiles02 = folder('02 Profiles', [

  // ── Drivers ──
  req(
    'Create Driver (unique via timestamp)',
    'POST',
    '{{baseUrl}}/api/drivers',
    {
      firstName: 'Test',
      lastName: 'Driver',
      email: 'driver_{{$timestamp}}@test.local',
      phone: '0600000099',
      licenseNumber: 'TST-{{$timestamp}}'
    },
    [
      "pm.test('Status 201', () => pm.response.to.have.status(201));",
      "const body = pm.response.json();",
      "pm.collectionVariables.set('newDriverId', body.id);",
      "pm.collectionVariables.set('newDriverEmail', body.email);",
      "pm.collectionVariables.set('newDriverLicense', body.licenseNumber);"
    ],
    'Creates a new driver with unique email/license via $timestamp. Saves values for PUT.'
  ),

  req(
    'GET All Drivers',
    'GET',
    '{{baseUrl}}/api/drivers',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "pm.test('Response is array', () => pm.expect(pm.response.json()).to.be.an('array'));"
    ]
  ),

  req(
    'GET Driver by ID',
    'GET',
    '{{baseUrl}}/api/drivers/{{newDriverId}}',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "pm.test('id matches', () => pm.expect(String(pm.response.json().id)).to.eql(String(pm.collectionVariables.get('newDriverId'))));"
    ]
  ),

  req(
    'PUT Driver (update, keep own email/license)',
    'PUT',
    '{{baseUrl}}/api/drivers/{{newDriverId}}',
    {
      firstName: 'TestUpdated',
      lastName: 'Driver',
      email: '{{newDriverEmail}}',
      phone: '0600000099',
      licenseNumber: '{{newDriverLicense}}'
    },
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "pm.test('firstName updated', () => pm.expect(pm.response.json().firstName).to.eql('TestUpdated'));"
    ],
    'Self-excluding uniqueness check: updating with own email/license must succeed.'
  ),

  req(
    'PUT Driver with another user email (409)',
    'PUT',
    '{{baseUrl}}/api/drivers/{{newDriverId}}',
    {
      firstName: 'TestUpdated',
      lastName: 'Driver',
      email: 'hamid@covehicle.local',
      phone: '0600000099',
      licenseNumber: '{{newDriverLicense}}'
    },
    [
      "pm.test('Status 409', () => pm.response.to.have.status(409));"
    ],
    'Rule: cannot update driver to use another user\'s email.'
  ),

  req(
    'Search Drivers q=hamid',
    'GET',
    '{{baseUrl}}/api/drivers/search?q=hamid',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const body = pm.response.json();",
      "pm.test('finds Hamid', () => pm.expect(body.some(d => d.firstName.toLowerCase() === 'hamid')).to.be.true);"
    ]
  ),

  req(
    'Search Drivers q=HAMID (case-insensitive)',
    'GET',
    '{{baseUrl}}/api/drivers/search?q=HAMID',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "pm.test('finds Hamid case-insensitively', () => pm.expect(pm.response.json().some(d => d.firstName.toLowerCase() === 'hamid')).to.be.true);"
    ]
  ),

  req(
    'Create Driver duplicate email (409)',
    'POST',
    '{{baseUrl}}/api/drivers',
    { firstName: 'Dup', lastName: 'Driver', email: 'hamid@covehicle.local',
      phone: '0600000099', licenseNumber: 'DUP-999' },
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"],
    'Rule: email must be unique across all users.'
  ),

  req(
    'Create Driver duplicate license (409)',
    'POST',
    '{{baseUrl}}/api/drivers',
    { firstName: 'Dup', lastName: 'Driver', email: 'unique_dup@test.local',
      phone: '0600000099', licenseNumber: 'HAM-001' },
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"],
    'Rule: license number must be unique.'
  ),

  req(
    'Create Driver blank firstName (400 + fieldErrors)',
    'POST',
    '{{baseUrl}}/api/drivers',
    { firstName: '', lastName: 'Driver', email: 'blank_fn@test.local',
      phone: '0600000099', licenseNumber: 'BLK-001' },
    [
      "pm.test('Status 400', () => pm.response.to.have.status(400));",
      "pm.test('fieldErrors.firstName present', () => {",
      "    const body = pm.response.json();",
      "    pm.expect(body.fieldErrors).to.have.property('firstName');",
      "});"
    ]
  ),

  req(
    'Create Driver invalid email format (400)',
    'POST',
    '{{baseUrl}}/api/drivers',
    { firstName: 'Bad', lastName: 'Email', email: 'not-an-email',
      phone: '0600000099', licenseNumber: 'BAD-001' },
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Create Driver malformed JSON body (400)',
    'POST',
    '{{baseUrl}}/api/drivers',
    null,
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"],
    'Sends malformed JSON to trigger parse error.',
    ["pm.request.body = { mode: 'raw', raw: '{bad' };"]
  ),

  req(
    'Create Driver missing body (400)',
    'POST',
    '{{baseUrl}}/api/drivers',
    null,
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"],
    'Sends POST with no body to trigger missing body error.',
    ["delete pm.request.body;"]
  ),

  req(
    'GET Driver not found (404)',
    'GET',
    '{{baseUrl}}/api/drivers/99999',
    null,
    ["pm.test('Status 404', () => pm.response.to.have.status(404));"]
  ),

  // ── Passengers ──
  req(
    'Create Passenger (unique via timestamp)',
    'POST',
    '{{baseUrl}}/api/passengers',
    {
      firstName: 'Test',
      lastName: 'Passenger',
      email: 'passenger_{{$timestamp}}@test.local',
      phone: '0610000099'
    },
    [
      "pm.test('Status 201', () => pm.response.to.have.status(201));",
      "const body = pm.response.json();",
      "pm.collectionVariables.set('newPassengerId', body.id);",
      "pm.collectionVariables.set('newPassengerEmail', body.email);"
    ]
  ),

  req(
    'GET All Passengers',
    'GET',
    '{{baseUrl}}/api/passengers',
    null,
    ["pm.test('Status 200', () => pm.response.to.have.status(200));"]
  ),

  req(
    'GET Passenger by ID',
    'GET',
    '{{baseUrl}}/api/passengers/{{newPassengerId}}',
    null,
    ["pm.test('Status 200', () => pm.response.to.have.status(200));"]
  ),

  req(
    'PUT Passenger (update own email)',
    'PUT',
    '{{baseUrl}}/api/passengers/{{newPassengerId}}',
    {
      firstName: 'TestUpdated',
      lastName: 'Passenger',
      email: '{{newPassengerEmail}}',
      phone: '0610000099'
    },
    ["pm.test('Status 200', () => pm.response.to.have.status(200));"]
  ),

  req(
    'PUT Passenger with another user email (409)',
    'PUT',
    '{{baseUrl}}/api/passengers/{{newPassengerId}}',
    {
      firstName: 'TestUpdated',
      lastName: 'Passenger',
      email: 'hamid@covehicle.local',
      phone: '0610000099'
    },
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"],
    'Rule: cannot update passenger to use another user\'s email.'
  ),

  req(
    'GET Passenger by email (Salma)',
    'GET',
    '{{baseUrl}}/api/passengers/by-email?email=salma@covehicle.local',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "pm.test('email matches', () => pm.expect(pm.response.json().email).to.eql('salma@covehicle.local'));"
    ]
  ),

  req(
    'GET Passenger by unknown email (404)',
    'GET',
    '{{baseUrl}}/api/passengers/by-email?email=nobody@test.local',
    null,
    ["pm.test('Status 404', () => pm.response.to.have.status(404));"]
  ),

  req(
    'Create Passenger duplicate email (409)',
    'POST',
    '{{baseUrl}}/api/passengers',
    { firstName: 'Dup', lastName: 'Pass', email: 'salma@covehicle.local', phone: '0610000099' },
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"]
  ),

  req(
    'Create Passenger with Driver email (cross-type uniqueness, 409)',
    'POST',
    '{{baseUrl}}/api/passengers',
    { firstName: 'Cross', lastName: 'Type', email: 'hamid@covehicle.local', phone: '0610000099' },
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"],
    'Rule: email is unique across both drivers and passengers.'
  )

], 'Driver and passenger CRUD, search, and validation scenarios.');

// ─── FOLDER 03 – Vehicles ────────────────────────────────────────────────────

const vehicles03 = folder('03 Vehicles', [

  req(
    'Create Vehicle for Driver 1 (unique plate)',
    'POST',
    '{{baseUrl}}/api/drivers/1/vehicles',
    { brand: 'Ford', model: 'Focus', color: 'Silver',
      licensePlate: 'TST-{{$timestamp}}', capacity: 4 },
    [
      "pm.test('Status 201', () => pm.response.to.have.status(201));",
      "const body = pm.response.json();",
      "pm.collectionVariables.set('newVehicleId', body.id);",
      "pm.collectionVariables.set('newVehiclePlate', body.licensePlate);"
    ]
  ),

  req(
    'GET Vehicles for Driver 1',
    'GET',
    '{{baseUrl}}/api/drivers/1/vehicles',
    null,
    ["pm.test('Status 200', () => pm.response.to.have.status(200));"]
  ),

  req(
    'GET Vehicle by ID',
    'GET',
    '{{baseUrl}}/api/drivers/1/vehicles/{{newVehicleId}}',
    null,
    ["pm.test('Status 200', () => pm.response.to.have.status(200));"]
  ),

  req(
    'PUT Vehicle (update, keep own plate)',
    'PUT',
    '{{baseUrl}}/api/drivers/1/vehicles/{{newVehicleId}}',
    { brand: 'Ford', model: 'Focus', color: 'Black',
      licensePlate: '{{newVehiclePlate}}', capacity: 4 },
    ["pm.test('Status 200', () => pm.response.to.have.status(200));"]
  ),

  req(
    'PUT Vehicle with another plate (409)',
    'PUT',
    '{{baseUrl}}/api/drivers/1/vehicles/{{newVehicleId}}',
    { brand: 'Ford', model: 'Focus', color: 'Black',
      licensePlate: 'HAM-1234', capacity: 4 },
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"],
    'Rule: cannot update vehicle to use another vehicle\'s plate.'
  ),

  req(
    'Create Vehicle duplicate plate (409)',
    'POST',
    '{{baseUrl}}/api/drivers/1/vehicles',
    { brand: 'Dacia', model: 'Logan', color: 'Grey',
      licensePlate: 'HAM-1234', capacity: 4 },
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"]
  ),

  req(
    'Create Vehicle capacity 9 (400 + fieldErrors.capacity)',
    'POST',
    '{{baseUrl}}/api/drivers/1/vehicles',
    { brand: 'Bus', model: 'Big', color: 'Yellow',
      licensePlate: 'BIG-001', capacity: 9 },
    [
      "pm.test('Status 400', () => pm.response.to.have.status(400));",
      "pm.test('fieldErrors.capacity present', () => {",
      "    pm.expect(pm.response.json().fieldErrors).to.have.property('capacity');",
      "});"
    ]
  ),

  req(
    'Create Vehicle missing capacity (400)',
    'POST',
    '{{baseUrl}}/api/drivers/1/vehicles',
    { brand: 'Ford', model: 'Ka', color: 'Red', licensePlate: 'KA-001' },
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Create Vehicle unknown driver (404)',
    'POST',
    '{{baseUrl}}/api/drivers/99999/vehicles',
    { brand: 'Ford', model: 'Ka', color: 'Red', licensePlate: 'KA-002', capacity: 4 },
    ["pm.test('Status 404', () => pm.response.to.have.status(404));"]
  ),

  req(
    'GET Vehicle unknown vehicle (404)',
    'GET',
    '{{baseUrl}}/api/drivers/1/vehicles/99999',
    null,
    ["pm.test('Status 404', () => pm.response.to.have.status(404));"]
  ),

  req(
    'Driver 2 GET Driver 1\'s vehicle (403)',
    'GET',
    '{{baseUrl}}/api/drivers/2/vehicles/1',
    null,
    ["pm.test('Status 403', () => pm.response.to.have.status(403));"],
    'Rule: driver can only access their own vehicles.'
  ),

  req(
    'Driver 2 PUT Driver 1\'s vehicle (403)',
    'PUT',
    '{{baseUrl}}/api/drivers/2/vehicles/1',
    { brand: 'Dacia', model: 'Logan', color: 'Grey',
      licensePlate: 'HAM-1234', capacity: 4 },
    ["pm.test('Status 403', () => pm.response.to.have.status(403));"]
  ),

  req(
    'Create fresh vehicle for Driver 1 (to delete)',
    'POST',
    '{{baseUrl}}/api/drivers/1/vehicles',
    { brand: 'Kia', model: 'Picanto', color: 'White',
      licensePlate: 'DEL-{{$timestamp}}', capacity: 4 },
    [
      "pm.test('Status 201', () => pm.response.to.have.status(201));",
      "pm.collectionVariables.set('deletableVehicleId', pm.response.json().id);"
    ]
  ),

  req(
    'DELETE fresh vehicle (204)',
    'DELETE',
    '{{baseUrl}}/api/drivers/1/vehicles/{{deletableVehicleId}}',
    null,
    ["pm.test('Status 204', () => pm.response.to.have.status(204));"]
  ),

  req(
    'DELETE vehicle 1 that has trips (409)',
    'DELETE',
    '{{baseUrl}}/api/drivers/1/vehicles/1',
    null,
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"],
    'Rule: cannot delete a vehicle that has associated trips.'
  ),

  req(
    'Driver 2 DELETE Driver 1\'s vehicle (403)',
    'DELETE',
    '{{baseUrl}}/api/drivers/2/vehicles/1',
    null,
    ["pm.test('Status 403', () => pm.response.to.have.status(403));"]
  )

], 'Vehicle CRUD under /api/drivers/{driverId}/vehicles with ownership and validation checks.');

// ─── FOLDER 04 – Trips and Stop Rules ────────────────────────────────────────

const trips04 = folder('04 Trips and Stop Rules', [

  req(
    'Create trip one stop only (400)',
    'POST',
    '{{baseUrl}}/api/drivers/1/trips',
    { vehicleId: 1, seatsOffered: 3,
      stops: [ stopBody('Rabat', '17:00', 0) ] },
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Create trip first stop in the past (400)',
    'POST',
    '{{baseUrl}}/api/drivers/1/trips',
    { vehicleId: 1, seatsOffered: 3,
      stops: [
        { city: 'Rabat',      arrivalTime: '2000-01-01T10:00:00', priceFromStart: 0 },
        { city: 'Casablanca', arrivalTime: '2000-01-01T11:00:00', priceFromStart: 40 }
      ] },
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Create trip stop times not strictly increasing (equal times, 400)',
    'POST',
    '{{baseUrl}}/api/drivers/1/trips',
    { vehicleId: 1, seatsOffered: 3,
      stops: [
        stopBody('Rabat',      '17:00', 0),
        stopBody('Casablanca', '17:00', 40)
      ] },
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Create trip duplicate city case-insensitive (400)',
    'POST',
    '{{baseUrl}}/api/drivers/1/trips',
    { vehicleId: 1, seatsOffered: 3,
      stops: [
        stopBody(' rabat ', '17:00', 0),
        stopBody('Rabat',   '18:00', 40)
      ] },
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Create trip first price not 0 (400)',
    'POST',
    '{{baseUrl}}/api/drivers/1/trips',
    { vehicleId: 1, seatsOffered: 3,
      stops: [
        stopBody('Rabat',      '17:00', 10),
        stopBody('Casablanca', '18:00', 40)
      ] },
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Create trip prices not strictly increasing (400)',
    'POST',
    '{{baseUrl}}/api/drivers/1/trips',
    { vehicleId: 1, seatsOffered: 3,
      stops: [
        stopBody('Rabat',      '17:00', 0),
        stopBody('Casablanca', '18:00', 40),
        stopBody('Settat',     '19:00', 30)
      ] },
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Create trip blank city (400 + fieldErrors)',
    'POST',
    '{{baseUrl}}/api/drivers/1/trips',
    { vehicleId: 1, seatsOffered: 3,
      stops: [
        { city: '', arrivalTime: `{{day5}}T17:00:00`, priceFromStart: 0 },
        stopBody('Casablanca', '18:00', 40)
      ] },
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Create trip empty stops list (400)',
    'POST',
    '{{baseUrl}}/api/drivers/1/trips',
    { vehicleId: 1, seatsOffered: 3, stops: [] },
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Create trip missing seatsOffered (400)',
    'POST',
    '{{baseUrl}}/api/drivers/1/trips',
    { vehicleId: 1,
      stops: [ stopBody('Rabat', '17:00', 0), stopBody('Casablanca', '18:00', 40) ] },
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Create trip unknown driver (404)',
    'POST',
    '{{baseUrl}}/api/drivers/99999/trips',
    { vehicleId: 1, seatsOffered: 3,
      stops: [ stopBody('Rabat', '17:00', 0), stopBody('Casablanca', '18:00', 40) ] },
    ["pm.test('Status 404', () => pm.response.to.have.status(404));"]
  ),

  req(
    'Create trip unknown vehicle (404)',
    'POST',
    '{{baseUrl}}/api/drivers/1/trips',
    { vehicleId: 99999, seatsOffered: 3,
      stops: [ stopBody('Rabat', '17:00', 0), stopBody('Casablanca', '18:00', 40) ] },
    ["pm.test('Status 404', () => pm.response.to.have.status(404));"]
  ),

  req(
    'GET Trip not found (404)',
    'GET',
    '{{baseUrl}}/api/trips/99999',
    null,
    ["pm.test('Status 404', () => pm.response.to.have.status(404));"]
  ),

  req(
    'GET Driver 1 trips (contains setup tripId)',
    'GET',
    '{{baseUrl}}/api/drivers/1/trips',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const tripId = pm.collectionVariables.get('tripId');",
      "pm.test('contains setup tripId', () => {",
      "    pm.expect(pm.response.json().some(t => String(t.id) === String(tripId))).to.be.true;",
      "});"
    ]
  ),

  req(
    'GET Driver 1 trip reservations (Salma CONFIRMED, Karim CANCELED)',
    'GET',
    '{{baseUrl}}/api/drivers/1/trips/{{tripId}}/reservations',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const body = pm.response.json();",
      "pm.test('contains Salma CONFIRMED', () => {",
      "    pm.expect(body.some(r => r.passengerId === 6 && r.status === 'CONFIRMED')).to.be.true;",
      "});",
      "pm.test('contains Karim CANCELED', () => {",
      "    pm.expect(body.some(r => r.passengerId === 7 && r.status === 'CANCELED')).to.be.true;",
      "});"
    ]
  ),

  req(
    'GET Driver 2 trip reservations for Driver 1 trip (403)',
    'GET',
    '{{baseUrl}}/api/drivers/2/trips/{{tripId}}/reservations',
    null,
    ["pm.test('Status 403', () => pm.response.to.have.status(403));"]
  ),

  req(
    'Driver 2 complete Driver 1 trip (403)',
    'POST',
    '{{baseUrl}}/api/drivers/2/trips/{{tripId}}/complete',
    null,
    ["pm.test('Status 403', () => pm.response.to.have.status(403));"]
  ),

  req(
    'Driver 2 cancel Driver 1 trip (403)',
    'POST',
    '{{baseUrl}}/api/drivers/2/trips/{{tripId}}/cancel',
    null,
    ["pm.test('Status 403', () => pm.response.to.have.status(403));"]
  ),

  req(
    'Complete seed trip 3 (already COMPLETED → 409)',
    'POST',
    '{{baseUrl}}/api/drivers/3/trips/3/complete',
    null,
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"]
  ),

  req(
    'Cancel seed trip 3 (already COMPLETED → 409)',
    'POST',
    '{{baseUrl}}/api/drivers/3/trips/3/cancel',
    null,
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"]
  ),

  req(
    'Cancel seed trip 4 (already CANCELED → 409)',
    'POST',
    '{{baseUrl}}/api/drivers/4/trips/4/cancel',
    null,
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"]
  )

], 'Trip creation validation rules and state-transition guards.');

// ─── FOLDER 05 – Search ──────────────────────────────────────────────────────

const search05 = folder('05 Search', [

  req(
    'Search maxPrice=60 Settat→Marrakech (contains tripId, segment 50)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day5}}&maxPrice=60',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const tripId = pm.collectionVariables.get('tripId');",
      "const found = pm.response.json().content.find(t => String(t.tripId) === String(tripId));",
      "pm.test('contains tripId (segment 50 <= 60)', () => pm.expect(found).to.not.be.undefined);"
    ]
  ),

  req(
    'Search maxPrice=60 Rabat→Marrakech (does NOT contain tripId, segment 120)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Rabat&arrivalCity=Marrakech&date={{day5}}&maxPrice=60',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const tripId = pm.collectionVariables.get('tripId');",
      "const found = pm.response.json().content.find(t => String(t.tripId) === String(tripId));",
      "pm.test('tripId NOT in results (segment 120 > 60)', () => pm.expect(found).to.be.undefined);"
    ]
  ),

  req(
    'Search maxPrice=120 Rabat→Marrakech (contains tripId, segmentPrice 120)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Rabat&arrivalCity=Marrakech&date={{day5}}&maxPrice=120',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const tripId = pm.collectionVariables.get('tripId');",
      "const found = pm.response.json().content.find(t => String(t.tripId) === String(tripId));",
      "pm.test('contains tripId with segmentPrice 120', () => {",
      "    pm.expect(found).to.not.be.undefined;",
      "    pm.expect(Number(found.segmentPrice)).to.eql(120);",
      "});"
    ]
  ),

  req(
    'Search seats=3 Settat→Marrakech (NOT in results, only 2 remaining)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day5}}&seats=3',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const tripId = pm.collectionVariables.get('tripId');",
      "const found = pm.response.json().content.find(t => String(t.tripId) === String(tripId));",
      "pm.test('tripId NOT in results (only 2 remaining)', () => pm.expect(found).to.be.undefined);"
    ]
  ),

  req(
    'Search seats=2 Settat→Marrakech (contains tripId)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day5}}&seats=2',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const tripId = pm.collectionVariables.get('tripId');",
      "const found = pm.response.json().content.find(t => String(t.tripId) === String(tripId));",
      "pm.test('contains tripId (2 seats available)', () => pm.expect(found).to.not.be.undefined);"
    ]
  ),

  req(
    'Search different date (day15, NOT in results)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day15}}',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const tripId = pm.collectionVariables.get('tripId');",
      "const found = pm.response.json().content.find(t => String(t.tripId) === String(tripId));",
      "pm.test('tripId NOT in results (wrong date)', () => pm.expect(found).to.be.undefined);"
    ],
    null,
    [
      "const d = new Date(); d.setDate(d.getDate() + 15);",
      "const pad = n => String(n).padStart(2,'0');",
      "pm.collectionVariables.set('day15', d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate()));"
    ]
  ),

  req(
    'Search reversed direction Marrakech→Rabat (NOT in results)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Marrakech&arrivalCity=Rabat&date={{day5}}',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const tripId = pm.collectionVariables.get('tripId');",
      "const found = pm.response.json().content.find(t => String(t.tripId) === String(tripId));",
      "pm.test('tripId NOT in results (reversed direction)', () => pm.expect(found).to.be.undefined);"
    ]
  ),

  req(
    'Search city case/spacing (%20settat%20 → MARRAKECH)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=%20settat%20&arrivalCity=MARRAKECH&date={{day5}}',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const tripId = pm.collectionVariables.get('tripId');",
      "const found = pm.response.json().content.find(t => String(t.tripId) === String(tripId));",
      "pm.test('contains tripId (case/space insensitive)', () => pm.expect(found).to.not.be.undefined);"
    ]
  ),

  req(
    'Search pagination size=1 page=0',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day5}}&size=1&page=0',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const body = pm.response.json();",
      "pm.test('content length <= 1', () => pm.expect(body.content.length).to.be.at.most(1));",
      "pm.test('has totalElements', () => pm.expect(body).to.have.property('totalElements'));",
      "pm.test('has totalPages', () => pm.expect(body).to.have.property('totalPages'));",
      "pm.test('has number', () => pm.expect(body).to.have.property('number'));",
      "pm.test('has size', () => pm.expect(body).to.have.property('size'));"
    ]
  ),

  req(
    'Search pagination page=999 (empty content)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day5}}&page=999',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "pm.test('empty content', () => pm.expect(pm.response.json().content).to.be.empty);"
    ]
  ),

  req(
    'Search validation seats=0 (400)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day5}}&seats=0',
    null,
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Search validation size=0 (400)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day5}}&size=0',
    null,
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Search validation size=500 (400)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day5}}&size=500',
    null,
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Search validation page=-1 (400)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day5}}&page=-1',
    null,
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Search validation blank departureCity (400)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=&arrivalCity=Marrakech&date={{day5}}',
    null,
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Search validation missing date param (400)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech',
    null,
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Search validation malformed date (400)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date=not-a-date',
    null,
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Search validation size=50 (200)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day5}}&size=50',
    null,
    ["pm.test('Status 200', () => pm.response.to.have.status(200));"]
  ),

  req(
    'Search validation size=51 (400)',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day5}}&size=51',
    null,
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Search result has required fields',
    'GET',
    '{{baseUrl}}/api/trips/search?departureCity=Settat&arrivalCity=Marrakech&date={{day5}}',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const body = pm.response.json();",
      "const trip = body.content[0];",
      "if (trip) {",
      "    pm.test('has tripId', () => pm.expect(trip).to.have.property('tripId'));",
      "    pm.test('has driverName', () => pm.expect(trip).to.have.property('driverName'));",
      "    pm.test('has vehicle.id', () => pm.expect(trip.vehicle).to.have.property('id'));",
      "    pm.test('has pickupCity', () => pm.expect(trip).to.have.property('pickupCity'));",
      "    pm.test('has pickupTime', () => pm.expect(trip).to.have.property('pickupTime'));",
      "    pm.test('has dropoffCity', () => pm.expect(trip).to.have.property('dropoffCity'));",
      "    pm.test('has dropoffTime', () => pm.expect(trip).to.have.property('dropoffTime'));",
      "    pm.test('has segmentPrice', () => pm.expect(trip).to.have.property('segmentPrice'));",
      "    pm.test('has remainingSeats', () => pm.expect(trip).to.have.property('remainingSeats'));",
      "    pm.test('has departureDate', () => pm.expect(trip).to.have.property('departureDate'));",
      "}"
    ]
  )

], 'Trip search filters, pagination, and validation.');

// ─── FOLDER 06 – Reservations ────────────────────────────────────────────────

const reservations06 = folder('06 Reservations', [

  req(
    'Salma books same trip again (409 - duplicate)',
    'POST',
    '{{baseUrl}}/api/passengers/6/reservations',
    { tripId: '{{tripId}}', pickupStopId: '{{stopSettat}}',
      dropoffStopId: '{{stopMarrakech}}', numberOfSeats: 1 },
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"],
    'Rule: one CONFIRMED reservation per passenger per trip.'
  ),

  req(
    'Book numberOfSeats=0 (400)',
    'POST',
    '{{baseUrl}}/api/passengers/9/reservations',
    { tripId: '{{tripId}}', pickupStopId: '{{stopSettat}}',
      dropoffStopId: '{{stopMarrakech}}', numberOfSeats: 0 },
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Book missing numberOfSeats (400)',
    'POST',
    '{{baseUrl}}/api/passengers/9/reservations',
    { tripId: '{{tripId}}', pickupStopId: '{{stopSettat}}',
      dropoffStopId: '{{stopMarrakech}}' },
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"]
  ),

  req(
    'Mehdi books 3 seats (only 2 free → 409)',
    'POST',
    '{{baseUrl}}/api/passengers/9/reservations',
    { tripId: '{{tripId}}', pickupStopId: '{{stopSettat}}',
      dropoffStopId: '{{stopMarrakech}}', numberOfSeats: 3 },
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"],
    'Rule: cannot book more seats than available.'
  ),

  req(
    'Book unknown passenger (404)',
    'POST',
    '{{baseUrl}}/api/passengers/99999/reservations',
    { tripId: '{{tripId}}', pickupStopId: '{{stopSettat}}',
      dropoffStopId: '{{stopMarrakech}}', numberOfSeats: 1 },
    ["pm.test('Status 404', () => pm.response.to.have.status(404));"]
  ),

  req(
    'Book unknown trip (404)',
    'POST',
    '{{baseUrl}}/api/passengers/9/reservations',
    { tripId: 99999, pickupStopId: '{{stopSettat}}',
      dropoffStopId: '{{stopMarrakech}}', numberOfSeats: 1 },
    ["pm.test('Status 404', () => pm.response.to.have.status(404));"]
  ),

  req(
    'Book unknown pickup stop (404)',
    'POST',
    '{{baseUrl}}/api/passengers/9/reservations',
    { tripId: '{{tripId}}', pickupStopId: 99999,
      dropoffStopId: '{{stopMarrakech}}', numberOfSeats: 1 },
    ["pm.test('Status 404', () => pm.response.to.have.status(404));"]
  ),

  req(
    'Book stop from another trip as pickup (400)',
    'POST',
    '{{baseUrl}}/api/passengers/9/reservations',
    { tripId: '{{tripId}}', pickupStopId: 5,
      dropoffStopId: '{{stopMarrakech}}', numberOfSeats: 1 },
    ["pm.test('Status 400', () => pm.response.to.have.status(400));"],
    'Rule: stop must belong to the specified trip. Stop 5 belongs to seed trip 2.'
  ),

  req(
    'Book seed trip 4 (CANCELED → 409)',
    'POST',
    '{{baseUrl}}/api/passengers/9/reservations',
    { tripId: 4, pickupStopId: 11, dropoffStopId: 12, numberOfSeats: 1 },
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"],
    'Rule: cannot book on a CANCELED trip.'
  ),

  req(
    'Book seed trip 3 (COMPLETED → 409)',
    'POST',
    '{{baseUrl}}/api/passengers/9/reservations',
    { tripId: 3, pickupStopId: 8, dropoffStopId: 10, numberOfSeats: 1 },
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"],
    'Rule: cannot book on a COMPLETED trip.'
  ),

  req(
    'Imane cancels Salma\'s reservation (403)',
    'DELETE',
    '{{baseUrl}}/api/passengers/8/reservations/{{salmaReservationId}}',
    null,
    ["pm.test('Status 403', () => pm.response.to.have.status(403));"],
    'Rule: passenger can only cancel their own reservations.'
  ),

  req(
    'Karim cancels already-canceled reservation (409)',
    'DELETE',
    '{{baseUrl}}/api/passengers/7/reservations/{{karimReservationId}}',
    null,
    ["pm.test('Status 409', () => pm.response.to.have.status(409));"],
    'Rule: cannot cancel an already-canceled reservation.'
  ),

  req(
    'Cancel unknown reservation (404)',
    'DELETE',
    '{{baseUrl}}/api/passengers/6/reservations/99999',
    null,
    ["pm.test('Status 404', () => pm.response.to.have.status(404));"]
  ),

  req(
    'GET Salma reservations (history, contains salmaReservationId)',
    'GET',
    '{{baseUrl}}/api/passengers/6/reservations',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "const body = pm.response.json();",
      "const salmaResId = pm.collectionVariables.get('salmaReservationId');",
      "pm.test('contains salmaReservationId', () => {",
      "    pm.expect(body.some(r => String(r.id) === String(salmaResId))).to.be.true;",
      "});"
    ]
  ),

  req(
    'GET reservations unknown passenger (404)',
    'GET',
    '{{baseUrl}}/api/passengers/99999/reservations',
    null,
    ["pm.test('Status 404', () => pm.response.to.have.status(404));"]
  ),

  req(
    'Mehdi books 2 seats Settat→Marrakech (trip FULL)',
    'POST',
    '{{baseUrl}}/api/passengers/9/reservations',
    { tripId: '{{tripId}}', pickupStopId: '{{stopSettat}}',
      dropoffStopId: '{{stopMarrakech}}', numberOfSeats: 2 },
    [
      "pm.test('Status 201', () => pm.response.to.have.status(201));",
      "const body = pm.response.json();",
      "pm.test('tripStatus FULL', () => pm.expect(body.tripStatus).to.eql('FULL'));",
      "pm.collectionVariables.set('mehdiReservationId', body.id);"
    ]
  ),

  req(
    'Mehdi cancels (trip back to PUBLISHED)',
    'DELETE',
    '{{baseUrl}}/api/passengers/9/reservations/{{mehdiReservationId}}',
    null,
    [
      "pm.test('Status 204', () => pm.response.to.have.status(204));"
    ]
  ),

  req(
    'GET trip after Mehdi cancels (PUBLISHED again)',
    'GET',
    '{{baseUrl}}/api/trips/{{tripId}}',
    null,
    [
      "pm.test('Status 200', () => pm.response.to.have.status(200));",
      "pm.test('status PUBLISHED', () => pm.expect(pm.response.json().status).to.eql('PUBLISHED'));"
    ]
  )

], 'Reservation rules: duplicates, capacity, ownership, trip state, and history.');

// ─── Assemble collection ──────────────────────────────────────────────────────

const collection = {
  info: {
    _postman_id: uid(),
    name: 'CoVehicle API',
    description: [
      'Complete test collection for the CoVehicle REST API (Spring Boot).',
      '',
      '## Prerequisites',
      'This collection assumes a freshly seeded database. Requests S10 and S11 (folder 01) depend on seed data and cannot be rerun without reseeding.',
      '',
      '## Run order',
      'Folders must be run in order: 00 → 01 → 02 → 03 → 04 → 05 → 06.',
      'Folder 00 creates the main test trip and saves its id and stop ids as collection variables.',
      'All subsequent folders depend on those variables.',
      '',
      '## Seed-dependent requests',
      '- **S10** (folder 01): depends on seed trip 5 departing in ~2 hours. Cannot be rerun without reseeding.',
      '- **S11** (folder 01): Youssef cancels seed trip 2. A second run returns 409 because the trip is already CANCELED. Reseed the database to rerun.',
      '',
      '## Reseeding',
      'Drop and recreate the database, then restart the app with the `local` or `docker` profile.',
      'The seeder runs automatically when the drivers table is empty.'
    ].join('\n'),
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'
  },
  variable: [
    { key: 'day5',               value: '', type: 'string' },
    { key: 'tripId',             value: '', type: 'string' },
    { key: 'stopRabat',          value: '', type: 'string' },
    { key: 'stopCasa',           value: '', type: 'string' },
    { key: 'stopSettat',         value: '', type: 'string' },
    { key: 'stopMarrakech',      value: '', type: 'string' },
    { key: 'salmaReservationId', value: '', type: 'string' },
    { key: 'karimReservationId', value: '', type: 'string' },
    { key: 'newDriverId',        value: '', type: 'string' },
    { key: 'newDriverEmail',     value: '', type: 'string' },
    { key: 'newDriverLicense',   value: '', type: 'string' },
    { key: 'newPassengerId',     value: '', type: 'string' },
    { key: 'newPassengerEmail',  value: '', type: 'string' },
    { key: 'newVehicleId',       value: '', type: 'string' },
    { key: 'newVehiclePlate',    value: '', type: 'string' },
    { key: 'deletableVehicleId', value: '', type: 'string' },
    { key: 'mehdiReservationId', value: '', type: 'string' },
    { key: 'day15',              value: '', type: 'string' },
    { key: 'secondVehicleId',    value: '', type: 'string' },
    { key: 'secondTripId',       value: '', type: 'string' }
  ],
  event: [
    { listen: 'prerequest', script: { type: 'text/javascript', exec: COLLECTION_PREREQ } },
    { listen: 'test',       script: { type: 'text/javascript', exec: COLLECTION_TEST  } }
  ],
  item: [ setup00, scenarios01, profiles02, vehicles03, trips04, search05, reservations06 ]
};

// ─── Environment ─────────────────────────────────────────────────────────────

const environment = {
  id: 'covehicle-local-env',
  name: 'CoVehicle Local',
  values: [
    { key: 'baseUrl',                  value: 'http://localhost:8080', enabled: true },
    { key: 'hamidId',                  value: '1',  enabled: true },
    { key: 'youssefId',                value: '2',  enabled: true },
    { key: 'fatimaId',                 value: '3',  enabled: true },
    { key: 'omarId',                   value: '4',  enabled: true },
    { key: 'saraId',                   value: '5',  enabled: true },
    { key: 'salmaId',                  value: '6',  enabled: true },
    { key: 'karimId',                  value: '7',  enabled: true },
    { key: 'imaneId',                  value: '8',  enabled: true },
    { key: 'mehdiId',                  value: '9',  enabled: true },
    { key: 'nadiaId',                  value: '10', enabled: true },
    { key: 'daciaId',                  value: '1',  enabled: true },
    { key: 'clioId',                   value: '2',  enabled: true },
    { key: 'peugeotId',                value: '3',  enabled: true },
    { key: 'yarisId',                  value: '4',  enabled: true },
    { key: 'i10Id',                    value: '5',  enabled: true },
    { key: 'seedTrip1Id',              value: '1',  enabled: true },
    { key: 'seedTrip2Id',              value: '2',  enabled: true },
    { key: 'seedTrip3Id',              value: '3',  enabled: true },
    { key: 'seedTrip4Id',              value: '4',  enabled: true },
    { key: 'seedTrip5Id',              value: '5',  enabled: true },
    { key: 'nadiaSeedReservationId',   value: '6',  enabled: true }
  ],
  _postman_variable_scope: 'environment',
  _postman_exported_at: new Date().toISOString(),
  _postman_exported_using: 'build_collection.js'
};

// ─── Write files ──────────────────────────────────────────────────────────────

const outDir = path.join(__dirname);
fs.writeFileSync(
  path.join(outDir, 'CoVehicle.postman_collection.json'),
  JSON.stringify(collection, null, 2),
  'utf8'
);
fs.writeFileSync(
  path.join(outDir, 'CoVehicle.local.postman_environment.json'),
  JSON.stringify(environment, null, 2),
  'utf8'
);

// ─── Summary ─────────────────────────────────────────────────────────────────

const totalRequests = collection.item.reduce((sum, f) => sum + f.item.length, 0);
console.log('✅  Generated:');
console.log('   postman/CoVehicle.postman_collection.json');
console.log('   postman/CoVehicle.local.postman_environment.json');
console.log(`   Folders : ${collection.item.length}`);
console.log(`   Requests: ${totalRequests}`);
collection.item.forEach(f => console.log(`     ${f.name} (${f.item.length} requests)`));
