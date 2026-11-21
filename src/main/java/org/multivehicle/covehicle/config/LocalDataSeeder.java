package org.multivehicle.covehicle.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.multivehicle.covehicle.entity.Admin;
import org.multivehicle.covehicle.entity.Reservation;
import org.multivehicle.covehicle.entity.Stop;
import org.multivehicle.covehicle.entity.Trip;
import org.multivehicle.covehicle.entity.Vehicle;
import org.multivehicle.covehicle.entity.enums.ReservationStatus;
import org.multivehicle.covehicle.entity.enums.TripStatus;
import org.multivehicle.covehicle.entity.users.Driver;
import org.multivehicle.covehicle.entity.users.Passenger;
import org.multivehicle.covehicle.repository.AdminRepository;
import org.multivehicle.covehicle.repository.DriverRepository;
import org.multivehicle.covehicle.repository.PassengerRepository;
import org.multivehicle.covehicle.repository.ReservationRepository;
import org.multivehicle.covehicle.repository.StopRepository;
import org.multivehicle.covehicle.repository.TripRepository;
import org.multivehicle.covehicle.repository.VehicleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@Profile({"local", "docker"})
@RequiredArgsConstructor
public class LocalDataSeeder implements CommandLineRunner {

    private final DriverRepository driverRepository;
    private final PassengerRepository passengerRepository;
    private final AdminRepository adminRepository;
    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final StopRepository stopRepository;
    private final ReservationRepository reservationRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (driverRepository.count() > 0) {
            log.info("Seed data skipped: drivers table is not empty");
            return;
        }

        Driver hamid = driver("Hamid", "Alami", "hamid@covehicle.local", "0600000001", "HAM-001");
        Driver youssef = driver("Youssef", "El Amrani", "youssef@covehicle.local", "0600000002", "YOU-002");
        Driver fatima = driver("Fatima", "Idrissi", "fatima@covehicle.local", "0600000003", "FAT-003");
        Driver omar = driver("Omar", "Benjelloun", "omar@covehicle.local", "0600000004", "OMA-004");
        Driver sara = driver("Sara", "El Fassi", "sara@covehicle.local", "0600000005", "SAR-005");

        Passenger salma = passenger("Salma", "Bennani", "salma@covehicle.local", "0610000001");
        Passenger karim = passenger("Karim", "Tazi", "karim@covehicle.local", "0610000002");
        Passenger imane = passenger("Imane", "Idrissi", "imane@covehicle.local", "0610000003");
        Passenger mehdi = passenger("Mehdi", "Alaoui", "mehdi@covehicle.local", "0610000004");
        Passenger nadia = passenger("Nadia", "Chraibi", "nadia@covehicle.local", "0610000005");

        admin("admin1@covehicle.local");
        admin("admin2@covehicle.local");
        admin("admin3@covehicle.local");
        admin("admin4@covehicle.local");
        admin("admin5@covehicle.local");

        Vehicle dacia = vehicle("Dacia", "Logan", "Grey", "HAM-1234", 4, hamid);
        Vehicle clio = vehicle("Renault", "Clio", "Blue", "YOU-2345", 4, youssef);
        Vehicle peugeot = vehicle("Peugeot", "208", "White", "FAT-3456", 4, fatima);
        Vehicle yaris = vehicle("Toyota", "Yaris", "Red", "OMA-4567", 3, omar);
        Vehicle i10 = vehicle("Hyundai", "i10", "Black", "SAR-5678", 4, sara);

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Trip hamidTrip = createTrip(dacia, 3, TripStatus.PUBLISHED,
                stop(" Rabat ", tomorrow.atTime(17, 0), "0"),
                stop(" Casablanca ", tomorrow.atTime(18, 15), "40"),
                stop(" Settat ", tomorrow.atTime(19, 15), "70"),
                stop(" Marrakech ", tomorrow.atTime(21, 30), "120"));

        LocalDateTime fullBase = LocalDate.now().plusDays(3).atTime(9, 0);
        Trip fullTrip = createTrip(clio, 2, TripStatus.FULL,
                stop("Rabat", fullBase, "0"),
                stop("Casablanca", fullBase.plusHours(1), "40"),
                stop("Marrakech", fullBase.plusHours(3), "120"));

        LocalDateTime completedBase = LocalDate.now().minusDays(1).atTime(8, 0);
        Trip completedTrip = createTrip(peugeot, 3, TripStatus.COMPLETED,
                stop("Casablanca", completedBase, "0"),
                stop("Settat", completedBase.plusMinutes(90), "30"),
                stop("Marrakech", completedBase.plusHours(4), "80"));

        LocalDateTime canceledBase = LocalDate.now().plusDays(4).atTime(10, 0);
        Trip canceledTrip = createTrip(yaris, 3, TripStatus.CANCELED,
                stop("Rabat", canceledBase, "0"),
                stop("Fes", canceledBase.plusMinutes(150), "60"));

        LocalDateTime soonBase = LocalDateTime.now().plusHours(2).withSecond(0).withNano(0);
        Trip soonTrip = createTrip(i10, 3, TripStatus.PUBLISHED,
                stop("Casablanca", soonBase, "0"),
                stop("Rabat", soonBase.plusMinutes(90), "50"));

        reservation(salma, fullTrip, 1, 2, 1, ReservationStatus.CONFIRMED);
        reservation(karim, fullTrip, 0, 2, 1, ReservationStatus.CONFIRMED);
        reservation(mehdi, completedTrip, 0, 2, 1, ReservationStatus.CONFIRMED);
        reservation(nadia, completedTrip, 0, 1, 1, ReservationStatus.CANCELED);
        reservation(imane, canceledTrip, 0, 1, 1, ReservationStatus.CANCELED);
        reservation(nadia, soonTrip, 0, 1, 1, ReservationStatus.CONFIRMED);

        log.info("Seeded data counts: drivers={}, passengers={}, admins={}, vehicles={}, trips={}, stops={}, reservations={}",
                driverRepository.count(), passengerRepository.count(), adminRepository.count(),
                vehicleRepository.count(), tripRepository.count(), stopRepository.count(),
                reservationRepository.count());
        log.info("Seeded people: Hamid={}, Youssef={}, Fatima={}, Omar={}, Sara={}, Salma={}, Karim={}, Imane={}, Mehdi={}, Nadia={}",
                hamid.getId(), youssef.getId(), fatima.getId(), omar.getId(), sara.getId(),
                salma.getId(), karim.getId(), imane.getId(), mehdi.getId(), nadia.getId());
        log.info("Seeded vehicles: Dacia Logan={}, Renault Clio={}, Peugeot 208={}, Toyota Yaris={}, Hyundai i10={}",
                dacia.getId(), clio.getId(), peugeot.getId(), yaris.getId(), i10.getId());
        log.info("Seeded trips: Hamid's trip={}, FULL={}, COMPLETED={}, CANCELED={}, departing in 2h={}",
                hamidTrip.getId(), fullTrip.getId(), completedTrip.getId(), canceledTrip.getId(), soonTrip.getId());

        for (Trip trip : List.of(hamidTrip, fullTrip, completedTrip, canceledTrip, soonTrip)) {
            for (Stop stop : trip.getStops()) {
                log.info("Seeded stop: tripId={}, city={}, stopOrder={}, id={}",
                        trip.getId(), stop.getCity(), stop.getStopOrder(), stop.getId());
            }
        }
    }

    private Driver driver(String firstName, String lastName, String email, String phone, String licenseNumber) {
        return driverRepository.save(Driver.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phone(phone)
                .licenseNumber(licenseNumber)
                .build());
    }

    private Passenger passenger(String firstName, String lastName, String email, String phone) {
        return passengerRepository.save(Passenger.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phone(phone)
                .build());
    }

    private void admin(String email) {
        adminRepository.save(Admin.builder().email(email).build());
    }

    private Vehicle vehicle(String brand, String model, String color, String licensePlate, int capacity, Driver driver) {
        return vehicleRepository.save(Vehicle.builder()
                .brand(brand)
                .model(model)
                .color(color)
                .licensePlate(licensePlate)
                .capacity(capacity)
                .driver(driver)
                .build());
    }

    private Trip createTrip(Vehicle vehicle, int seatsOffered, TripStatus status, Stop... stops) {
        Trip trip = Trip.builder()
                .departureDate(stops[0].getArrivalTime())
                .seatsOffered(seatsOffered)
                .status(status)
                .vehicle(vehicle)
                .build();

        for (int i = 0; i < stops.length; i++) {
            stops[i].setStopOrder(i);
            trip.addStop(stops[i]);
        }
        return tripRepository.save(trip);
    }

    private Stop stop(String city, LocalDateTime arrivalTime, String priceFromStart) {
        return Stop.builder()
                .city(city.trim())
                .arrivalTime(arrivalTime)
                .priceFromStart(new BigDecimal(priceFromStart))
                .build();
    }

    private void reservation(Passenger passenger, Trip trip, int pickupIndex, int dropoffIndex,
                             int numberOfSeats, ReservationStatus status) {
        Stop pickup = trip.getStops().get(pickupIndex);
        Stop dropoff = trip.getStops().get(dropoffIndex);
        BigDecimal totalAmount = dropoff.getPriceFromStart()
                .subtract(pickup.getPriceFromStart())
                .multiply(BigDecimal.valueOf(numberOfSeats))
                .setScale(2, RoundingMode.HALF_UP);

        reservationRepository.save(Reservation.builder()
                .numberOfSeats(numberOfSeats)
                .totalAmount(totalAmount)
                .status(status)
                .pickupStop(pickup)
                .dropoffStop(dropoff)
                .trip(trip)
                .passenger(passenger)
                .build());
    }
}
