package org.matsim.run;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import org.matsim.analysis.KelheimMainModeIdentifier;
import org.matsim.analysis.ModeChoiceCoverageControlerListener;
import org.matsim.analysis.personMoney.PersonMoneyEventsAnalysisModule;
import org.matsim.analysis.postAnalysis.drt.DrtServiceQualityAnalysis;
import org.matsim.analysis.postAnalysis.drt.DrtVehiclesRoadUsageAnalysis;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonScoreEvent;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.MATSimApplication;
import org.matsim.application.analysis.CheckPopulation;
import org.matsim.application.analysis.traffic.LinkStats;
import org.matsim.application.analysis.travelTimeValidation.TravelTimeAnalysis;
import org.matsim.application.options.SampleOptions;
import org.matsim.application.prepare.CreateLandUseShp;
import org.matsim.application.prepare.freight.tripExtraction.ExtractRelevantFreightTrips;
import org.matsim.application.prepare.network.CreateNetworkFromSumo;
import org.matsim.application.prepare.population.*;
import org.matsim.application.prepare.pt.CreateTransitScheduleFromGtfs;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.DrtConfigs;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtModule;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.contrib.dvrp.trafficmonitoring.DvrpModeLimitedMaxSpeedTravelTimeModule;



import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.replanning.choosers.ForceInnovationStrategyChooser;
import org.matsim.core.replanning.choosers.StrategyChooser;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.drtFare.KelheimDrtFareModule;
import org.matsim.extensions.pt.routing.ptRoutingModes.PtIntermodalRoutingModesConfigGroup;
import org.matsim.modechoice.InformedModeChoiceConfigGroup;
import org.matsim.modechoice.InformedModeChoiceModule;
import org.matsim.modechoice.ModeAvailability;
import org.matsim.modechoice.ModeOptions;
import org.matsim.modechoice.commands.GenerateChoiceSet;
import org.matsim.modechoice.constraints.RelaxedSubtourConstraint;
import org.matsim.modechoice.estimators.DailyConstantFixedCosts;
import org.matsim.modechoice.estimators.DefaultLegScoreEstimator;
import org.matsim.modechoice.estimators.LegEstimator;
import org.matsim.run.prepare.PrepareNetwork;
import org.matsim.run.prepare.PreparePopulation;
import org.matsim.run.utils.KelheimCaseStudyTool;
import org.matsim.run.utils.StrategyWeightFadeout;
import org.matsim.vehicles.VehicleType;
import picocli.CommandLine;
import playground.vsp.pt.fare.DistanceBasedPtFareParams;
import playground.vsp.pt.fare.PtFareConfigGroup;
import playground.vsp.pt.fare.PtTripFareEstimator;
import playground.vsp.scoring.IncomeDependentUtilityOfMoneyPersonScoringParameters;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@CommandLine.Command(header = ":: Open Kelheim Scenario ::", version = RunKelheimScenario.VERSION, mixinStandardHelpOptions = true)
@MATSimApplication.Prepare({
        CreateNetworkFromSumo.class, CreateTransitScheduleFromGtfs.class, TrajectoryToPlans.class, GenerateShortDistanceTrips.class,
        MergePopulations.class, ExtractRelevantFreightTrips.class, DownSamplePopulation.class, PrepareNetwork.class, ExtractHomeCoordinates.class,
        CreateLandUseShp.class, ResolveGridCoordinates.class, PreparePopulation.class, CleanPopulation.class, GenerateChoiceSet.class
})
@MATSimApplication.Analysis({
        TravelTimeAnalysis.class, LinkStats.class, CheckPopulation.class, DrtServiceQualityAnalysis.class, DrtVehiclesRoadUsageAnalysis.class
})
public class RunKelheimScenario extends MATSimApplication {

    static final String VERSION = "2.x";

    @CommandLine.Mixin
    private final SampleOptions sample = new SampleOptions(25, 10, 1);

    @CommandLine.Option(names = "--with-drt", defaultValue = "false", description = "enable DRT service")
    private boolean drt;

    @CommandLine.Option(names = "--income-dependent", defaultValue = "true", description = "enable income dependent monetary utility", negatable = true)
    private boolean incomeDependent;

    @CommandLine.Option(names = "--av-fare", defaultValue = "2.0", description = "AV fare (euro per trip)")
    private double avFare;

    @CommandLine.Option(names = "--case-study", defaultValue = "NULL", description = "Case study for the av scenario")
    private KelheimCaseStudyTool.AV_SERVICE_AREAS avServiceArea;

    @CommandLine.Option(names = "--bike-rnd", defaultValue = "false", description = "enable randomness in ASC of bike")
    private boolean bikeRnd;

    @CommandLine.Option(names = "--random-seed", defaultValue = "4711", description = "setting random seed for the simulation")
    private long randomSeed;

    @CommandLine.Option(names = "--intermodal", defaultValue = "false", description = "enable DRT service")
    private boolean intermodal;

    @CommandLine.Option(names = "--plans", defaultValue = "", description = "Use different input plans")
    private String planOrigin;

    @CommandLine.ArgGroup(exclusive = false, multiplicity = "0..1", heading = "Strategy Options\n")
    StrategyOptions strategy = new StrategyOptions();

    public RunKelheimScenario(@Nullable Config config) {
        super(config);
    }

    public RunKelheimScenario() {
        super(String.format("scenarios/input/kelheim-v%s-25pct.config.xml", VERSION));
    }

    public static void main(String[] args) {
        MATSimApplication.run(RunKelheimScenario.class, args);
    }

    @Nullable
    @Override
    protected Config prepareConfig(Config config) {

        for (long ii = 600; ii <= 97200; ii += 600) {

            for (String act : List.of("home", "restaurant", "other", "visit", "errands", "accomp_other", "accomp_children",
                    "educ_higher", "educ_secondary", "educ_primary", "educ_tertiary", "educ_kiga", "educ_other")) {
                config.planCalcScore()
                        .addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams(act + "_" + ii).setTypicalDuration(ii));
            }

            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("work_" + ii).setTypicalDuration(ii)
                    .setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("business_" + ii).setTypicalDuration(ii)
                    .setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("leisure_" + ii).setTypicalDuration(ii)
                    .setOpeningTime(9. * 3600.).setClosingTime(27. * 3600.));

            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("shop_daily_" + ii).setTypicalDuration(ii)
                    .setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("shop_other_" + ii).setTypicalDuration(ii)
                    .setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
        }

        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("car interaction").setTypicalDuration(60));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("other").setTypicalDuration(600 * 3));

        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("freight_start").setTypicalDuration(60 * 15));
        config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("freight_end").setTypicalDuration(60 * 15));

        config.controler().setOutputDirectory(sample.adjustName(config.controler().getOutputDirectory()));
        config.plans().setInputFile(sample.adjustName(config.plans().getInputFile()));
        config.controler().setRunId(sample.adjustName(config.controler().getRunId()));

        config.qsim().setFlowCapFactor(sample.getSize() / 100.0);
        config.qsim().setStorageCapFactor(sample.getSize() / 100.0);

        config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.info);
        config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink);

        config.global().setRandomSeed(randomSeed);

        if (intermodal) {
            ConfigUtils.addOrGetModule(config, PtIntermodalRoutingModesConfigGroup.class);
        }

        if (drt) {
            MultiModeDrtConfigGroup multiModeDrtConfig = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
            ConfigUtils.addOrGetModule(config, DvrpConfigGroup.class);
            DrtConfigs.adjustMultiModeDrtConfig(multiModeDrtConfig, config.planCalcScore(), config.plansCalcRoute());
        }

        PtFareConfigGroup ptFareConfigGroup = ConfigUtils.addOrGetModule(config, PtFareConfigGroup.class);
        DistanceBasedPtFareParams distanceBasedPtFareParams = ConfigUtils.addOrGetModule(config, DistanceBasedPtFareParams.class);

        // Set parameters
        ptFareConfigGroup.setApplyUpperBound(true);
        ptFareConfigGroup.setUpperBoundFactor(1.5);

        distanceBasedPtFareParams.setMinFare(2.0);  // Minimum fare (e.g. short trip or 1 zone ticket)
        distanceBasedPtFareParams.setLongDistanceTripThreshold(50000); // Division between long trip and short trip (unit: m)
        distanceBasedPtFareParams.setNormalTripSlope(0.00017); // y = ax + b --> a value, for short trips
        distanceBasedPtFareParams.setNormalTripIntercept(1.6); // y = ax + b --> b value, for short trips
        distanceBasedPtFareParams.setLongDistanceTripSlope(0.00025); // y = ax + b --> a value, for long trips
        distanceBasedPtFareParams.setLongDistanceTripIntercept(30); // y = ax + b --> b value, for long trips

        InformedModeChoiceConfigGroup imc = ConfigUtils.addOrGetModule(config, InformedModeChoiceConfigGroup.class);
        imc.setTopK(strategy.k);

        addRunOption(config, "mc", strategy.modeChoice);

        if (strategy.modeChoice == ModeChoice.bestKSelection ||strategy.modeChoice == ModeChoice.informedModeChoice) {
            addRunOption(config, "k", strategy.k);
        }

        if (strategy.massConservation)
            addRunOption(config, "mass-conv");

        if (!strategy.timeMutation)
            addRunOption(config, "no-tm");

        if (iterations != -1)
            addRunOption(config, "iter", iterations);

        if (!planOrigin.isBlank()) {
            config.plans().setInputFile(
                    config.plans().getInputFile().replace(".plans", ".plans-" + planOrigin)
            );

            addRunOption(config, planOrigin);
        }

        return config;
    }

    @Override
    protected void prepareScenario(Scenario scenario) {

        for (Link link : scenario.getNetwork().getLinks().values()) {
            Set<String> modes = link.getAllowedModes();

            // allow freight traffic together with cars
            if (modes.contains("car")) {
                HashSet<String> newModes = Sets.newHashSet(modes);
                newModes.add("freight");

                link.setAllowedModes(newModes);
            }
        }

        if (drt) {
            scenario.getPopulation()
                    .getFactory()
                    .getRouteFactories()
                    .setRouteFactory(DrtRoute.class, new DrtRouteFactory());
        }

        if (bikeRnd) {
            Random bicycleRnd = new Random(8765);
            for (Person person : scenario.getPopulation().getPersons().values()) {
                double width = 2; //TODO this value is to be determined
                double number = width * (bicycleRnd.nextGaussian());
                person.getAttributes().putAttribute("bicycleLove", number);
            }
        }

    }

    @Override
    protected void prepareControler(Controler controler) {
        Config config = controler.getConfig();
        Network network = controler.getScenario().getNetwork();

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                install(new KelheimPtFareModule());
                install(new SwissRailRaptorModule());
                install(new PersonMoneyEventsAnalysisModule());

                bind(AnalysisMainModeIdentifier.class).to(KelheimMainModeIdentifier.class);
                addControlerListenerBinding().to(ModeChoiceCoverageControlerListener.class);

                // Configure mode-choice strategy
                addControlerListenerBinding().to(StrategyWeightFadeout.class).in(Singleton.class);
                Multibinder<StrategyWeightFadeout.Schedule> schedules = Multibinder.newSetBinder(binder(), StrategyWeightFadeout.Schedule.class);

                // Always collect all strategies (without the common MCs first)
                List<StrategyConfigGroup.StrategySettings> strategies = config.strategy().getStrategySettings().stream()
                        .filter(s -> !s.getStrategyName().equals(DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice) &&
                                    !s.getStrategyName().equals(DefaultPlanStrategiesModule.DefaultStrategy.ChangeSingleTripMode) &&
                                    !s.getStrategyName().equals(DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator)
                                ).collect(Collectors.toList());

                if (strategy.timeMutation) {
                    strategies.add(new StrategyConfigGroup.StrategySettings()
                            .setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator)
                            .setSubpopulation("person")
                            .setWeight(0.025)
                    );
                }

                if (strategy.modeChoice != ModeChoice.none) {

                    strategies.add(new StrategyConfigGroup.StrategySettings()
                            .setStrategyName(strategy.modeChoice.name)
                            .setSubpopulation("person")
                            .setWeight(strategy.weight)
                    );

                    schedules.addBinding().toInstance(new StrategyWeightFadeout.Schedule(strategy.modeChoice.name, "person", 0.75, 0.85));

                    InformedModeChoiceModule.Builder builder = InformedModeChoiceModule.newBuilder()
                            .withFixedCosts(DailyConstantFixedCosts.class, TransportMode.car)
                            .withLegEstimator(DefaultLegScoreEstimator.class, ModeOptions.AlwaysAvailable.class, TransportMode.bike, TransportMode.ride, TransportMode.walk)
                            .withLegEstimator(DefaultLegScoreEstimator.class, ModeOptions.ConsiderIfCarAvailable.class, TransportMode.car)
                            .withTripEstimator(PtTripFareEstimator.class, ModeOptions.AlwaysAvailable.class, TransportMode.pt);

	                if (strategy.massConservation)
		                builder.withConstraint(RelaxedSubtourConstraint.class);

                    install(builder.build());

                }


                // reset und set new strategies
                config.strategy().clearStrategySettings();
                strategies.forEach(s -> config.strategy().addStrategySettings(s));

                schedules.addBinding().toInstance(new StrategyWeightFadeout.Schedule(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute, "person", 0.78));

                bind(new TypeLiteral<StrategyChooser<Plan, Person>>() {}).toInstance(new ForceInnovationStrategyChooser<>(strategy.forceInnovation, true));

                if (incomeDependent) {
                    bind(ScoringParametersForPerson.class).to(IncomeDependentUtilityOfMoneyPersonScoringParameters.class).asEagerSingleton();
                }

                if (bikeRnd) {
                    addEventHandlerBinding().toInstance(new PersonDepartureEventHandler() {
                        @Inject
                        EventsManager events;
                        @Inject
                        Population population;

                        @Override
                        public void handleEvent(PersonDepartureEvent event) {
                            if (event.getLegMode().equals(TransportMode.bike)) {
                                double bicycleLove = (double) population.getPersons().get(event.getPersonId()).getAttributes().getAttribute("bicycleLove");
                                events.processEvent(new PersonScoreEvent(event.getTime(), event.getPersonId(), bicycleLove, "bicycleLove"));
                            }
                        }
                    });
                }
            }
        });

        if (drt) {
            MultiModeDrtConfigGroup multiModeDrtConfig = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
            controler.addOverridingModule(new DvrpModule());
            controler.addOverridingModule(new MultiModeDrtModule());
            controler.configureQSimComponents(DvrpQSimComponents.activateAllModes(multiModeDrtConfig));

            // Add speed limit to av vehicle
            double maxSpeed = controler.getScenario()
                    .getVehicles()
                    .getVehicleTypes()
                    .get(Id.create("autonomous_vehicle", VehicleType.class))
                    .getMaximumVelocity();
            controler.addOverridingModule(
                    new DvrpModeLimitedMaxSpeedTravelTimeModule("av", config.qsim().getTimeStepSize(),
                            maxSpeed));

            for (DrtConfigGroup drtCfg : multiModeDrtConfig.getModalElements()) {
                controler.addOverridingModule(new KelheimDrtFareModule(drtCfg, network, avFare));
                if (drtCfg.getMode().equals("av")) {
                    KelheimCaseStudyTool.setConfigFile(config, drtCfg, avServiceArea);
                }
            }

//            if (intermodal){
//                controler.addOverridingModule(new IntermodalTripFareCompensatorsModule());
//                controler.addOverridingModule(new PtIntermodalRoutingModesModule());
//                controler.addOverridingModule(new AbstractModule() {
//                    @Override
//                    public void install() {
//                        bind(RaptorIntermodalAccessEgress.class).to(EnhancedRaptorIntermodalAccessEgress.class);
//                    }
//                });
//            }
        }
    }


    public static final class StrategyOptions {

        @CommandLine.Option(names = {"--mode-choice", "--mc"}, defaultValue = "subTourModeChoice", description = "Mode choice strategy: ${COMPLETION-CANDIDATES}")
        private ModeChoice modeChoice = ModeChoice.subTourModeChoice;

        @CommandLine.Option(names = "--weight", defaultValue = "0.10", description = "Mode-choice strategy weight")
        private double weight;

        @CommandLine.Option(names = "--top-k", defaultValue = "5", description = "Top k options for some of the strategies")
        private int k;

        @CommandLine.Option(names = "--time-mutation", defaultValue = "true", description = "Enable time mutation strategy", negatable = true)
        private boolean timeMutation;

        @CommandLine.Option(names = "--mass-conservation", defaultValue = "false", description = "Enable mass conservation constraint", negatable = true)
        private boolean massConservation;

        @CommandLine.Option(names = "--force-innovation", defaultValue = "10", description = "Force innovative strategy with this %")
        private int forceInnovation;

    }

    public enum ModeChoice {

        none ("none"),
        changeSingleTrip (DefaultPlanStrategiesModule.DefaultStrategy.ChangeSingleTripMode),
        subTourModeChoice (DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice),
        bestChoice (InformedModeChoiceModule.BEST_CHOICE_STRATEGY),
        bestKSelection (InformedModeChoiceModule.BEST_K_SELECTION_STRATEGY),
        informedModeChoice (InformedModeChoiceModule.INFORMED_MODE_CHOICE);

        private final String name;

        ModeChoice(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

}