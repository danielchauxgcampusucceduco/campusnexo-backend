package edu.campusnexo.infrastructure.web;

import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.ClimateRecommendationUseCase;
import edu.campusnexo.application.port.in.DashboardQueryUseCase;
import edu.campusnexo.application.port.in.OccupancyEstimationUseCase;
import edu.campusnexo.application.port.in.TelemetryUseCase;
import edu.campusnexo.domain.model.CampusSpace;
import edu.campusnexo.domain.model.ClimateRecommendation;
import edu.campusnexo.domain.model.OccupancyEstimate;
import edu.campusnexo.domain.model.Telemetry;
import edu.campusnexo.domain.policy.ClimateContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1")
public class TelemetryController {
    private final TelemetryUseCase telemetry;
    private final CampusCatalogUseCase catalog;
    private final OccupancyEstimationUseCase occupancyEstimation;
    private final ClimateRecommendationUseCase climateRecommendations;
    private final DashboardQueryUseCase dashboard;
    private final Clock clock;

    public TelemetryController(TelemetryUseCase telemetry,
                               CampusCatalogUseCase catalog,
                               OccupancyEstimationUseCase occupancyEstimation,
                               ClimateRecommendationUseCase climateRecommendations,
                               DashboardQueryUseCase dashboard,
                               Clock clock) {
        this.telemetry = telemetry;
        this.catalog = catalog;
        this.occupancyEstimation = occupancyEstimation;
        this.climateRecommendations = climateRecommendations;
        this.dashboard = dashboard;
        this.clock = clock;
    }

    public record TelemetryRequest(
            @NotBlank @Size(max = 32) String spaceId,
            Instant recordedAt,
            @NotNull @Min(0) Integer peopleCount,
            @NotNull @Min(0) Integer objectCount,
            @NotNull @Min(0) Integer wifiDevices,
            @NotNull @DecimalMin("0") @DecimalMax("10000") Double co2Ppm,
            @NotNull @DecimalMin("-30") @DecimalMax("60") Double temperatureC,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double humidityPct,
            @NotNull @DecimalMin("0") @DecimalMax("20000") Double illuminanceLux,
            @NotNull @DecimalMin("0") @DecimalMax("10000") Double activePowerKw) {
        Telemetry toDomain(Instant defaultRecordedAt) {
            Instant effectiveRecordedAt = recordedAt == null ? defaultRecordedAt : recordedAt;
            return new Telemetry(spaceId, effectiveRecordedAt, peopleCount, objectCount, wifiDevices,
                    co2Ppm, temperatureC, humidityPct, illuminanceLux, activePowerKw);
        }
    }

    public record OccupancyEstimateRequest(
            @Min(0) @Max(1000) Integer yoloPeople,
            @NotNull @DecimalMin("0") @DecimalMax("10000") Double co2Ppm,
            @NotNull @Min(0) @Max(10000) Integer wifiDevices,
            @DecimalMin("300") @DecimalMax("1000") Double outdoorCo2Ppm) {}

    public record ClimateRecommendationRequest(
            @NotNull @Min(0) @Max(1000) Integer occupancy,
            @NotNull @DecimalMin("0") @DecimalMax("10000") Double co2Ppm,
            @NotNull @DecimalMin("-20") @DecimalMax("60") Double indoorTemperatureC,
            @DecimalMin("-30") @DecimalMax("60") Double outsideTemperatureC,
            @NotNull @DecimalMin("0") @DecimalMax("20000") Double illuminanceLux,
            boolean hvacOn,
            @NotNull @Min(0) @Max(10000) Integer minutesInCurrentHvacState) {}

    @PostMapping("/telemetry")
    @ResponseStatus(HttpStatus.CREATED)
    public Telemetry ingest(@Valid @RequestBody TelemetryRequest request) {
        return telemetry.ingest(request.toDomain(clock.instant()));
    }

    @GetMapping("/telemetry")
    public List<Telemetry> recentTelemetry(
            @RequestParam(required = false) @Size(max = 32) String spaceId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return telemetry.recent(spaceId, limit);
    }

    @PostMapping("/occupancy/estimate")
    public OccupancyEstimate estimateOccupancy(
            @RequestParam @NotBlank @Size(max = 32) String spaceId,
            @Valid @RequestBody OccupancyEstimateRequest request) {
        return occupancyEstimation.estimate(spaceId, request.yoloPeople(), request.co2Ppm(),
                request.wifiDevices(), request.outdoorCo2Ppm() == null ? 415.0 : request.outdoorCo2Ppm());
    }

    @PostMapping("/climate/recommendations")
    public ClimateRecommendation recommendClimate(
            @RequestParam @NotBlank @Size(max = 32) String spaceId,
            @Valid @RequestBody ClimateRecommendationRequest request) {
        CampusSpace space = catalog.getRequiredSpace(spaceId);
        ClimateContext context = new ClimateContext(request.occupancy(), request.co2Ppm(),
                request.indoorTemperatureC(),
                request.outsideTemperatureC() == null ? space.averageOutsideTempC() : request.outsideTemperatureC(),
                request.illuminanceLux(), request.hvacOn(), request.minutesInCurrentHvacState());
        return climateRecommendations.recommend(spaceId, context);
    }

    @GetMapping("/climate/recommendations")
    public List<ClimateRecommendation> recentClimateRecommendations(
            @RequestParam(required = false) @Size(max = 32) String spaceId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return climateRecommendations.recent(spaceId, limit);
    }

    @GetMapping("/spaces")
    public List<CampusSpace> spaces() {
        return catalog.listSpaces();
    }

    @GetMapping("/dashboard/summary")
    public DashboardQueryUseCase.DashboardSummary summary() {
        return dashboard.summarizeLast24Hours();
    }
}
