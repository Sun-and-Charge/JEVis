/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEDemo.
 * <p>
 * JEDemo is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEDemo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDemo. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDemo is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedemo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;
import org.jevis.commons.cli.AbstractCliApp;
import org.jevis.jedemo.costs.CostSetup;
import org.jevis.jedemo.costs.SetupReport;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Service "JEDemo": keeps the public demo organisation current (see docs/DEMO.md).
 * <ul>
 * <li>{@code -sm service}: one cycle per "Cycle Time" while the service object is enabled;</li>
 * <li>{@code -sm complete}: one cycle for the configured organisations;</li>
 * <li>{@code -sm single -jid <organisation ids>}: one cycle for the given organisations (settings from the service
 * object).</li>
 * </ul>
 * {@code -o dryrun=true} only logs what would be written. {@code -o invoicesFrom=2025-01} also bills the invoice cases of
 * the months from then on (backfill, existing invoices are skipped), {@code -o invoicesOnly=true} skips the replay and
 * the date rolling.
 * <p>
 * {@code -sm single -jid <organisation id> -o costSetup=dryrun|apply} carries out the setup plan of the demo cost cases
 * once instead of a cycle ({@link CostSetup}, docs/DEMO-COSTS.md): {@code dryrun} only reports, {@code apply} backs up
 * and writes. The report goes to {@code -o costSetupReport=<directory>} (default {@code var/log}), the backup to
 * {@code -o costSetupBackup=<directory>} (default {@code var/backup/costdemo-<time>}).
 */
public class Launcher extends AbstractCliApp {

    private static final Logger logger = LogManager.getLogger(Launcher.class);
    private static final String APP_INFO = "JEDemo";
    static final String OPTION_DRY_RUN = "dryrun";
    static final String OPTION_INVOICES_FROM = "invoicesFrom";
    static final String OPTION_INVOICES_ONLY = "invoicesOnly";
    static final String OPTION_COST_SETUP = "costSetup";
    static final String OPTION_COST_SETUP_REPORT = "costSetupReport";
    static final String OPTION_COST_SETUP_BACKUP = "costSetupBackup";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int DEFAULT_CYCLE_TIME = 3600000;

    private final Command commands = new Command();
    private DemoJob job;
    private boolean firstCycle = true;
    private boolean dryRun;
    /**
     * "dryrun" or "apply" for the cost demo setup, null for the normal job
     */
    private String costSetup;

    private Launcher(String[] args, String appname) {
        super(args, appname);
    }

    public static void main(String[] args) {
        logger.info("-------Start JEDemo-------");
        System.setProperty("user.timezone", "UTC");
        Launcher app = new Launcher(args, APP_INFO);
        app.execute();
    }

    @Override
    protected void addCommands() {
        comm.addObject(commands);
    }

    @Override
    protected void handleAdditionalCommands() {
        APP_SERVICE_CLASS_NAME = DemoConfig.CLASS_NAME;
        cycleTime = DEFAULT_CYCLE_TIME;
        dryRun = Boolean.parseBoolean(settings.options.getOrDefault(OPTION_DRY_RUN, "false").trim());
        if (dryRun) logger.info("Dry run: nothing is written");
        YearMonth invoicesFrom = null;
        String from = settings.options.get(OPTION_INVOICES_FROM);
        if (from != null && !from.trim().isEmpty()) {
            try {
                invoicesFrom = YearMonth.parse(from.trim());
                logger.info("Invoice cases are billed from {} on", invoicesFrom);
            } catch (DateTimeParseException e) {
                logger.error("Invalid option {}={} (expected yyyy-MM), ignored", OPTION_INVOICES_FROM, from);
            }
        }
        boolean invoicesOnly = Boolean.parseBoolean(settings.options.getOrDefault(OPTION_INVOICES_ONLY, "false").trim());
        job = new DemoJob(ds, dryRun, Clock.systemUTC(), invoicesFrom, invoicesOnly);
        String setup = settings.options.get(OPTION_COST_SETUP);
        if (setup != null && !setup.trim().isEmpty()) costSetup = setup.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The cost demo setup for one organisation (single mode only).
     */
    private void runCostSetup(List<Long> ids) {
        if (!CostSetup.DRY_RUN.equals(costSetup) && !CostSetup.APPLY.equals(costSetup)) {
            logger.error("Invalid option {}={} (expected {} or {}), nothing done", OPTION_COST_SETUP, costSetup, CostSetup.DRY_RUN, CostSetup.APPLY);
            return;
        }
        if (ids == null || ids.size() != 1) {
            logger.error("{} needs exactly one organisation: -sm single -jid <organisation id>", OPTION_COST_SETUP);
            return;
        }
        boolean apply = CostSetup.APPLY.equals(costSetup) && !dryRun;
        if (CostSetup.APPLY.equals(costSetup) && dryRun) logger.info("{}=apply with {}=true: dry run", OPTION_COST_SETUP, OPTION_DRY_RUN);
        String stamp = STAMP.format(LocalDateTime.now(ReplayPlanner.ZONE));
        Path reportDir = Paths.get(settings.options.getOrDefault(OPTION_COST_SETUP_REPORT, "var/log").trim());
        Path backupDir = Paths.get(settings.options.getOrDefault(OPTION_COST_SETUP_BACKUP, "var/backup/costdemo-" + stamp).trim());
        try {
            CostSetup setup = new CostSetup(ds, apply, Clock.systemUTC(), CostSetup.classpathPlan(), CostSetup::classpathResource, backupDir);
            SetupReport report = setup.run(ids.get(0));
            Path file = report.write(reportDir, "costdemo-setup-" + (apply ? "apply" : "dryrun") + "-" + stamp);
            logger.info("Cost demo setup: {}", report.getResult());
            logger.info("Report: {} (and .json)", file.toAbsolutePath());
            if (report.getBackupDirectory() != null) logger.info("Backup: {}", report.getBackupDirectory());
            int i = 1;
            for (String step : report.getManualSteps()) logger.info("Manual step {}: {}", i++, step);
        } catch (Exception e) {
            logger.error("Cost demo setup failed", e);
        }
    }

    private JEVisObject serviceObject() throws JEVisException {
        JEVisClass serviceClass = ds.getJEVisClass(APP_SERVICE_CLASS_NAME);
        if (serviceClass == null) throw new JEVisException("Class " + APP_SERVICE_CLASS_NAME + " missing", 404);
        List<JEVisObject> services = ds.getObjects(serviceClass, false);
        if (services.isEmpty()) throw new JEVisException("No " + APP_SERVICE_CLASS_NAME + " service object", 404);
        if (services.size() > 1) logger.warn("{} service objects found, using {}", services.size(), services.get(0).getID());
        return services.get(0);
    }

    private void cycle(List<Long> organisations) {
        try {
            if (!firstCycle) ds.reloadObjects();
            firstCycle = false;
            job.runCycle(serviceObject(), organisations);
        } catch (Exception e) {
            logger.error("JEDemo cycle failed", e);
        }
    }

    @Override
    protected void runSingle(List<Long> ids) {
        if (costSetup != null) {
            runCostSetup(ids);
            return;
        }
        cycle(ids);
    }

    @Override
    protected void runComplete() {
        if (costSetup != null) {
            logger.error("{} only runs with -sm single -jid <organisation id>, nothing done", OPTION_COST_SETUP);
            return;
        }
        cycle(null);
    }

    @Override
    protected void runServiceHelp() {
        if (costSetup != null) {
            logger.error("{} only runs with -sm single -jid <organisation id>, the service is not started", OPTION_COST_SETUP);
            System.exit(2);
        }
        if (checkConnection()) {
            getCycleTimeFromService(APP_SERVICE_CLASS_NAME);
            if (checkServiceStatus(APP_SERVICE_CLASS_NAME)) {
                setServiceStatus(APP_SERVICE_CLASS_NAME, 2L);
                cycle(null);
                setServiceStatus(APP_SERVICE_CLASS_NAME, 1L);
            } else {
                logger.info("Service is disabled.");
            }
        }
        sleep();
    }
}
