/*
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.machine.reference;

import java.io.File;
import java.text.DecimalFormat;
import java.util.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openpnp.gui.JogControlsPanel;
import org.openpnp.gui.support.Wizard;
import org.openpnp.machine.reference.wizards.ReferencePnpJobProcessorConfigurationWizard;
import org.openpnp.model.BoardLocation;
import org.openpnp.model.Configuration;
import org.openpnp.model.Job;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Panel;
import org.openpnp.model.Part;
import org.openpnp.model.Placement;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.FiducialLocator;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.PartAlignment;
import org.openpnp.spi.PnpJobPlanner;
import org.openpnp.spi.PnpJobProcessor.JobPlacement.Status;
import org.openpnp.spi.base.AbstractJobProcessor;
import org.openpnp.spi.base.AbstractPnpJobProcessor;
import org.openpnp.util.Collect;
import org.openpnp.util.FiniteStateMachine;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.Utils2D;
import org.openpnp.util.VisionUtils;
import org.pmw.tinylog.Logger;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

@Root
public class ReferencePnpJobProcessor extends AbstractPnpJobProcessor {
 
    enum State {
        Uninitialized,
        PreFlight,
        TipCalibration,
        FiducialCheck,
        Plan,
        ChangeNozzleTip,
        Feed,
        Pick,
        Align,
        Place,
        Cleanup,
        Stopped
    }

    enum Message {
        Initialize,
        Next,
        Complete,
        Abort,
        Skip,
        IgnoreContinue,
        Reset
    }

    public enum JobOrderHint {
        PartHeight,
        Part
    }
  
    public static class PlannedPlacement {
        public final JobPlacement jobPlacement;
        public final Nozzle nozzle;
        public Feeder feeder;
        public PartAlignment.PartAlignmentOffset alignmentOffsets;
        public boolean fed; //outdated, to remove
        public boolean disableAlignment = false;
        public boolean stepComplete;

        public PlannedPlacement(Nozzle nozzle, JobPlacement jobPlacement) {
            this.nozzle = nozzle;
            this.jobPlacement = jobPlacement;
        }

        @Override
        public String toString() {
            return nozzle + " -> " + jobPlacement.toString();
        }
    }



    @Attribute(required = false)
    protected boolean parkWhenComplete = false;
    
    @Element(required = false)
    protected boolean autoSaveJob = true;
    
    @Element(required = false)
    boolean autoSaveConfiguration = true;
    
    @Element(required = false)
    long configSaveFrequencyMs = (10 * 60 * 1000);

    @Attribute(required = false)
    protected JobOrderHint jobOrder = JobOrderHint.PartHeight;

    @Element(required = false)
    public PnpJobPlanner planner = new SimplePnpJobPlanner();
    
    @Element(required = false)
    boolean disableAutomatics = false;
    
    @Element(required = false)
    boolean autoSkipDisabledFeeders = false;
    
    @Element(required = false)
    boolean autoDisableFeeder = false;
    
    @Element(required = false)
	public
    static boolean disableTipChanging = false;
    
//    @Attribute(required=false)    
//    protected static int sizeThreshold = 100000;

    private FiniteStateMachine<State, Message> fsm = new FiniteStateMachine<>(State.Uninitialized);

    protected Job job;

    protected Machine machine;

    protected Head head;

    protected List<JobPlacement> jobPlacements = new ArrayList<>();

    protected List<PlannedPlacement> plannedPlacements = new ArrayList<>();
    
    protected Set<BoardLocation> completedFidChecks = new HashSet<>();
    
    long startTime;
    int totalPartsPlaced;
    int totalPartsSkipped;
    boolean makeSkip;
    
    long lastConfigSavedTimeMs = 0;
    int cycles = 0;
    int nozzleTipChanges = 0;

    public ReferencePnpJobProcessor() {
        fsm.add(State.Uninitialized, Message.Initialize, State.PreFlight, this::doInitialize);

        fsm.add(State.PreFlight, Message.Next, State.TipCalibration, this::doPreFlight);
        fsm.add(State.PreFlight, Message.Abort, State.Cleanup, Message.Next);
        
        fsm.add(State.TipCalibration, Message.Next, State.FiducialCheck, this::doTipCalibration);
        fsm.add(State.TipCalibration, Message.Skip, State.FiducialCheck, Message.Next);
        fsm.add(State.TipCalibration, Message.Abort, State.Cleanup, Message.Next);

        fsm.add(State.FiducialCheck, Message.Next, State.Plan, this::doFiducialCheck);
        fsm.add(State.FiducialCheck, Message.Skip, State.Plan, Message.Next);
        fsm.add(State.FiducialCheck, Message.Abort, State.Cleanup, Message.Next);

        fsm.add(State.Plan, Message.Next, State.ChangeNozzleTip, this::doPlan, Message.Next);
        fsm.add(State.Plan, Message.Abort, State.Cleanup, Message.Next);
        fsm.add(State.Plan, Message.Complete, State.Cleanup, Message.Next);

        fsm.add(State.ChangeNozzleTip, Message.Next, State.Feed, this::doChangeNozzleTip);
        fsm.add(State.ChangeNozzleTip, Message.Skip, State.ChangeNozzleTip, this::doSkip, Message.Next);
        fsm.add(State.ChangeNozzleTip, Message.Abort, State.Cleanup, Message.Next);

        fsm.add(State.Feed, Message.Next, State.Align, this::doFeedAndPick);
        fsm.add(State.Feed, Message.Skip, State.Feed, this::doSkip, Message.Next);
        fsm.add(State.Feed, Message.IgnoreContinue, State.Feed, this::doIgnoreContinue, Message.Next);
        fsm.add(State.Feed, Message.Abort, State.Cleanup, Message.Next);

        // TODO: See notes on doFeedAndPick()
        // fsm.add(State.Feed, Message.Next, State.Pick, this::doFeed, Message.Next);
        // fsm.add(State.Feed, Message.Skip, State.Feed, this::doSkip, Message.Next);
        // fsm.add(State.Feed, Message.Abort, State.Cleanup, Message.Next);
        //
        // fsm.add(State.Pick, Message.Next, State.Align, this::doPick, Message.Next);
        // fsm.add(State.Pick, Message.Skip, State.Pick, this::doSkip, Message.Next);
        // fsm.add(State.Pick, Message.Abort, State.Cleanup, Message.Next);

        fsm.add(State.Align, Message.Next, State.Place, this::doAlign);
        fsm.add(State.Align, Message.Skip, State.Align, this::doSkip, Message.Next);
        fsm.add(State.Align, Message.IgnoreContinue, State.Align, this::doIgnoreContinue, Message.Next);
        fsm.add(State.Align, Message.Abort, State.Cleanup, Message.Next);

        fsm.add(State.Place, Message.Next, State.Plan, this::doPlace);
        fsm.add(State.Place, Message.Skip, State.Place, this::doSkip, Message.Next);
        fsm.add(State.Place, Message.Abort, State.Cleanup, Message.Next);

        fsm.add(State.Cleanup, Message.Next, State.Stopped, this::doCleanup, Message.Reset);

        fsm.add(State.Stopped, Message.Reset, State.Uninitialized, this::doReset);
    }

    public synchronized void initialize(Job job) throws Exception {
        this.job = job;
        fsm.send(Message.Initialize);
    }

    public synchronized boolean next() throws Exception {

        try{
            fsm.send(Message.Next);
        } 
        catch (Exception e) {
            if(makeSkip && !isDisableAutomatics()) {
            	doSkip();
            }
            else {
                makeSkip=false;
                this.fireJobState(this.machine.getSignalers(), AbstractJobProcessor.State.ERROR);
                throw(e);
            } 
        }

        if (fsm.getState() == State.Stopped) {
            /*
             * If we've reached the Stopped state the process is complete. We reset the FSM and
             * return false to indicate that we're finished.
             */
            fsm.send(Message.Reset);
            return false;
        }
        else if (fsm.getState() == State.Plan && isJobComplete()) {
            /*
             * If we've reached this, we are near finished,
             * now put all automatically skipped parts back to parts ready for assembly !!!
             * Throwing the message we also prevent the Job against to be automatically 
             * finished if there are some parts pending to be assembled.
             * Using the script we may generate the list showing the list of skipped parts.
             */
                int i=0;
                 for(JobPlacement job: getSkippedJobPlacements()) {
                     job.status=Status.Pending;
                     i++; }
                        if(i!=0) {
                            Configuration.get().getScripting().on("Job.SkipList", null);
                            throw new Exception(""+i+" Parts skipped. Operator action required to finish assembling.");
                        }        
        
            /*
             * If we've reached the Plan state and there are no more placements to work on the job
             * is complete. We send the Complete Message to start the cleanup process.
             */
            fsm.send(Message.Complete);
            this.fireJobState(this.machine.getSignalers(), AbstractJobProcessor.State.FINISHED);
            return false;
        }

        return true;
    }

    public synchronized void abort() throws Exception {
        fsm.send(Message.Abort);
    }

    public synchronized void skip() throws Exception {
        fsm.send(Message.Skip);
    }
    
    public synchronized void ignoreContinue() throws Exception {
        fsm.send(Message.IgnoreContinue);
    }

    /*
     * TODO Due to the Align Skip issue I think we'd be better off replacing this API with
     * something like List<Message> getOptions(). This would return a list of options that the
     * caller can take at a given step. Need to figure out a way to make this generic enough
     * that other JP implementations can use it, thus it's probably not appropriate to just
     * use Message, but instead maybe a PnpJobProcessor specific enum.
     * Options would be things like:
     *      * Skip Placement
     *      * Try Later
     *      * Retry Action
     *      * Continue (Next)
     *      
     * Really just need to think about the way a user will want to respond to various error
     * conditions that arise in each step and see if these can be generalized in a meaningful
     * way.
     */
    public boolean canSkip() {
        return fsm.canSend(Message.Skip);
    }

    public boolean canIgnoreContinue() {
        return fsm.canSend(Message.IgnoreContinue);
    }

    /**
     * Validate that there is a job set before allowing it to start.
     * 
     * @throws Exception
     */
    protected void doInitialize() throws Exception {
        if (job == null) {
            throw new Exception("Can't initialize with a null Job.");
        }
    }

    /**
     * Create some internal shortcuts to various buried objects.
     * 
     * Check for obvious setup errors in the job: Feeders are available and enabled, Placements all
     * have valid parts, Parts all have height values set, Each part has at least one compatible
     * nozzle tip.
     * 
     * Populate the jobPlacements list with all the placements that we'll perform for the entire
     * job.
     * 
     * Safe-Z the machine, discard any currently picked parts.
     * 
     * @throws Exception
     */
    protected void doPreFlight() throws Exception {
        startTime = System.currentTimeMillis();
        totalPartsPlaced = 0;
        totalPartsSkipped = 0;
        cycles = 0;
        nozzleTipChanges = 0;
        saveJobAndConfig(true);
        
        // Create some shortcuts for things that won't change during the run
        this.machine = Configuration.get().getMachine();
        this.head = this.machine.getDefaultHead();
        this.jobPlacements.clear();
        this.completedFidChecks.clear();
        

        fireTextStatus("Checking job for setup errors.");

        for (BoardLocation boardLocation : job.getBoardLocations()) {
            // Only check enabled boards
            if (!boardLocation.isEnabled()) {
                continue;
            }
            
            // Check for ID duplicates - throw error if any are found
            HashSet<String> idlist = new HashSet<String>();
            for (Placement placement : boardLocation.getBoard().getPlacements()) {
                if (idlist.contains(placement.getId())) {
                    throw new Exception(String.format("This board contains at least one duplicate ID entry: %s ",
                        placement.getId()));
                } else {
                    idlist.add(placement.getId());
                }
            }
            
            for (Placement placement : boardLocation.getBoard().getPlacements()) {
                // Ignore placements that aren't set to be placed
                if (placement.getType() != Placement.Type.Place) {
                    continue;
                }
                
                // Ignore placements that are placed already
                if (boardLocation.getPlaced(placement.getId())) {
                    continue;
                }

                // Ignore placements that aren't on the side of the board we're processing.
                if (placement.getSide() != boardLocation.getSide()) {
                    continue;
                }

                JobPlacement jobPlacement = new JobPlacement(boardLocation, placement);

                // Make sure the part is not null
                if (placement.getPart() == null) {
                    throw new Exception(String.format("Part not found for board %s, placement %s.",
                            boardLocation.getBoard().getName(), placement.getId()));
                }

                // Verify that the part height is greater than zero. Catches a common configuration
                // error.
                if (placement.getPart().getHeight().getValue() <= 0D) {
                    throw new Exception(String.format("Part height for %s must be greater than 0.",
                            placement.getPart().getId()));
                }

                // Make sure there is at least one compatible nozzle tip available
                findNozzleTip(head, placement.getPart());

                // Make sure there is at least one compatible and enabled feeder available
                findFeeder(machine, placement.getPart());

                jobPlacements.add(jobPlacement);
            }
        }

        // Everything looks good, so prepare the machine.
        fireTextStatus("Preparing machine.");

        // Safe Z the machine
        head.moveToSafeZ();
        // Discard any currently picked parts
        discardAll(head);
        
        HashMap<String, Object> params = new HashMap<>();
        params.put("job", job);
        params.put("jobProcessor", this);
        Configuration.get().getScripting().on("Job.Starting", params);
    }
    
    protected void doTipCalibration() throws Exception {
        fireTextStatus("Performing nozzle tip calibration.");
        
        // calibrating nozzle tips currently on head
        for (Head head : machine.getHeads()) {
            for (Nozzle nozzle : head.getNozzles()) {
                NozzleTip nozzleTip = nozzle.getNozzleTip();
                if (nozzleTip == null) {
                    continue;
                }
                if (!nozzleTip.isCalibrated()) {
                    Logger.debug("Calibrating nozzle tip {}", nozzleTip);
                    nozzleTip.calibrate();
                }
            }
        }
    }
    
    public static boolean topLightFlag = false;
    public static Actuator topLight;
    
    public static void doTopLightOn() throws Exception { // top light control added
    	topLight = Configuration.get().getMachine().getActuatorByName("DownCamLights");
        if (topLight != null && !topLightFlag) {
        	Logger.debug("Turning on the light of Downlooking Camera");
        	topLight.actuate(true);
        	topLightFlag = true;
        }
    }
        	
    public static void doTopLightOff() throws Exception { // top light control added        	
    	if (topLight != null && topLightFlag) {
    		Logger.debug("Turning off the light of Downlooking Camera");        	
    		topLight.actuate(false);
        	topLightFlag = false;
    	}
    }
    
    protected void doFiducialCheck() throws Exception {
        fireTextStatus("Performing fiducial checks.");
        doTopLightOn();  //it's for any case if the "fiducialing" could be manually repeated.

        FiducialLocator locator = Configuration.get().getMachine().getFiducialLocator();
        
        if (job.isUsingPanel() && job.getPanels().get(0).isCheckFiducials()){
            Panel p = job.getPanels().get(0);
        
            BoardLocation boardLocation = job.getBoardLocations().get(0);
        
            locator.locateBoard(boardLocation, p.isCheckFiducials());
            Logger.debug("Panel Fiducial check for {}", boardLocation);
        }
        
        for (BoardLocation boardLocation : job.getBoardLocations()) {
            if (!boardLocation.isEnabled()) {
                continue;
            }
            if (!boardLocation.isCheckFiducials()) {
                continue;
            }
            if (completedFidChecks.contains(boardLocation)) {
                continue;
            }
            locator.locateBoard(boardLocation);
            Logger.debug("Fiducial check for {}", boardLocation);
            completedFidChecks.add(boardLocation);
            }
        doTopLightOff();
    }
    
    protected void doIndividualFiducialCheck(BoardLocation boardLocation) throws Exception {
        fireTextStatus("Performing individual fiducial check.");
        doTopLightOn(); //it's for any case if the "fiducialing" could be manually repeated.

        FiducialLocator locator = Configuration.get().getMachine().getFiducialLocator();
        
        locator.locateBoard(boardLocation);
        Logger.debug("Fiducial check for {}", boardLocation);
        doTopLightOff();
    }

    /**
     * Description of the planner:
     * 
     * 1. Create a List<List<JobPlacement>> where each List<JobPlacement> is a List of JobPlacements
     * that the corresponding (in order) Nozzle can handle in Nozzle order.
     * 
     * In addition, each List<JobPlacement> contains one instance of null which represents a
     * solution where that Nozzle does not perform a placement.
     * 
     * 2. Create the Cartesian product of all of the List<JobPlacement>. The resulting List<List
     * <JobPlacement>> represents possible solutions for a single cycle with each JobPlacement
     * corresponding to a Nozzle.
     * 
     * 3. Filter out any solutions where the same JobPlacement is represented more than once. We
     * don't want more than one Nozzle trying to place the same Placement.
     * 
     * 4. Sort the solutions by fewest nulls followed by fewest nozzle changes. The result is that
     * we prefer solutions that use more nozzles in a cycle and require fewer nozzle changes.
     * 
     * Note: TODO: Originally planned to have this sort by part height but that went out the window
     * during development. Need to think about how to best combine the height requirement with the
     * want to fill all nozzles and perform minimal nozzle changes. Based on IRC discussion, the
     * part height thing might be a red herring - most machines will have enough Z to place all
     * parts regardless of height order.
     */
    protected void doPlan() throws Exception {
        plannedPlacements.clear();

        fireTextStatus("Planning placements.");
        doTopLightOff(); //not necessary as should be already off - but for any case to avoid the fuck up the pipeline.

        List<JobPlacement> jobPlacements;

        if (this.jobOrder.equals(JobOrderHint.Part)) {
            // Get the list of unfinished placements and sort them by part.
                jobPlacements = getPendingJobPlacements().stream()
                        .sorted(Comparator.comparing(JobPlacement::getPartId))
                        .collect(Collectors.toList());
        } 
        else {
            // Get the list of unfinished placements and sort them by part height.
                jobPlacements = getPendingJobPlacements().stream()
                        .sorted(Comparator.comparing(JobPlacement::getPartHeight))
                        .collect(Collectors.toList());
        }

        if (jobPlacements.isEmpty()) {
            return;
        }

        long t = System.currentTimeMillis();
        List<JobPlacement> result = planner.plan(head, jobPlacements);
        Logger.debug("Planner complete in {}ms: {}", (System.currentTimeMillis() - t), result);

        // Now we have a solution, so apply it to the nozzles and plan the placements.
        for (Nozzle nozzle : head.getNozzles()) {
            // The solution is in Nozzle order, so grab the next one.
            JobPlacement jobPlacement = result.remove(0);
            if (jobPlacement == null) {
                continue;
            }
            jobPlacement.status = Status.Processing;
            plannedPlacements.add(new PlannedPlacement(nozzle, jobPlacement));
        }
        
        if (plannedPlacements.size() == 0) {
            throw new Exception("No placements planned. That's an uh oh.");
        }

        Logger.debug("Planned placements {}", plannedPlacements);
        cycles++;
    }
    
    protected void doChangeNozzleTip() throws Exception {
        fireTextStatus("Checking nozzle tips.");
        for (PlannedPlacement plannedPlacement : plannedPlacements) {
            if (plannedPlacement.stepComplete) {
                continue;
            }

            Nozzle nozzle = plannedPlacement.nozzle;
            JobPlacement jobPlacement = plannedPlacement.jobPlacement;
            Placement placement = jobPlacement.placement;
            Part part = placement.getPart();

            // If the currently loaded NozzleTip can handle the Part we're good.
            if (nozzle.getNozzleTip() != null && nozzle.getNozzleTip().canHandle(part)) {
                Logger.debug("No nozzle change needed for nozzle {}", nozzle);
                plannedPlacement.stepComplete = true;
                continue;
            }

            fireTextStatus("Changing nozzle tip on nozzle %s.", nozzle.getId());

            // Otherwise find a compatible tip and load it
            NozzleTip nozzleTip = findNozzleTip(nozzle, part);
            fireTextStatus("Change NozzleTip on Nozzle %s to %s.", 
                    nozzle.getId(), 
                    nozzleTip.getName());   
            Logger.debug("Change NozzleTip on Nozzle {} from {} to {}",
                    new Object[] {nozzle, nozzle.getNozzleTip(), nozzleTip});
            nozzle.unloadNozzleTip();
            nozzle.loadNozzleTip(nozzleTip);
            
            // calibrate nozzle after change
            if (nozzleTip != null) {
                if (!nozzleTip.isCalibrated()) {
                    Logger.debug("Calibrating nozzle tip {} after change.", nozzleTip);
                    nozzleTip.calibrate();
                }
            }
            
            // Mark this step as complete
            plannedPlacement.stepComplete = true;
            
            nozzleTipChanges++;
        }

        clearStepComplete();
    }

    /*
     * TODO: This method is a compromise due to time constraints. Below, there is doFeed and doPick,
     * which were intended to be used in sequence. I realized too late that I had made an error in
     * designing the FSM and for multiple nozzles it was doing feed, feed, pick, pick instead of
     * feed, pick, feed, pick. The latter is correct while the former is useless. Since I need to
     * release this feature before Maker Faire I've decided to just combine the methods to get this
     * done.
     * 
     * The whole FSM system needs to be reconsidered. There are two main things to consider: 1.
     * current FSM cannot handle transitions within action methods. If it could then we could have
     * doFeed process one PlannedPlacement, continue to Pick and then have Pick either loop back to
     * Feed if there are more PlannedPlacements or continue to Align if not. I don't love this idea
     * because it makes the FSM non-deterministic and thus harder to reason about.
     * 
     * 2. An ideal system would treat each step that required actions for multiple PlannedPlacements
     * as their own FSM, producing a hierarchy of FSMs. I've also seen this idea referred to as
     * "fork and join" FSMs and I have brainstormed this type of system a bit in the image at:
     * https://imgur.com/a/63Y1t
     */

    /*
     * INFO: This is modified doFeedAndPick.
     * If feeding fails it is repeated with programmable counter (Feed Retry Count) the same as 
     * it was made before.
     * But when picking fails it is also repeated now with new with new programmable counter 
     * (Pick Retry Count). 
     * If all this fails and user choose <try again> the part is again feeded and picked.
    */	    
    protected void doFeedAndPick() throws Exception {
        for (PlannedPlacement plannedPlacement : plannedPlacements) {
            if (plannedPlacement.stepComplete) {
                continue;
            }
 
            subFeedAndPick(plannedPlacement); //I have extracted all this code to private void
                                              //because will need identical procedure to use in
                                              //new doAlign() containing picking the missing part.
                                              //this way I don't duplicate the code and will have 
                                              //more transparency.
            plannedPlacement.stepComplete = true;
        }

        clearStepComplete();
    }

    /*
     * INFO: This is the code extracted from original doFeedAndPick.
     * Exactly the same procedure I need in both doFeedAndPick and new doAlign.
     * So I did it to don't duplicate the code and get better transparency in 
     * doFeedAndPick and in new doAlign.
     */
    private void subFeedAndPick(PlannedPlacement plannedPlacement) throws Exception{
        Nozzle nozzle = plannedPlacement.nozzle;
        JobPlacement jobPlacement = plannedPlacement.jobPlacement;
        Placement placement = jobPlacement.placement;
        Part part = placement.getPart();

        Exception lastError = null;
        Feeder lastErrorFeeder = null;
        while (true) {
           // Find a compatible, enabled feeder
           Feeder feeder;
           try {
               feeder = findFeeder(machine, part);
           }
           catch (Exception e) { 
               if (lastError != null) { 
                   throw new Exception(String.format("Unable to feed %s. Feeder %s: %s.", 
                       part.getId(), 
                       lastErrorFeeder.getName(), 
                       lastError.getMessage()), 
                       lastError);
               }
               else {
                   if (isAutoSkipDisabledFeeders()) {
                       makeSkip=true; 
                   }
                   throw new Exception(String.format("Unable to feed %s. No enabled feeder found.", part.getId()));
                   }
               }
               
               // Feed the part
               plannedPlacement.feeder = feeder;
               try {
                   // Try to feed the part. If it fails, retry the specified number of times
                   // before giving up.
                   retry(1 + feeder.getRetryCount(), () -> { 
                       fireTextStatus("Feeding %s from %s for %s.", part.getId(),
                               feeder.getName(), placement.getId());
                               Logger.debug("Attempt Feed {} from {} with {}.",
                               new Object[] {part, feeder, nozzle});

                       feeder.feed(nozzle);
                       fireTextStatus("Fed %s from %s for %s.", part.getId(),
                               feeder.getName(), placement.getId());
                       Logger.debug("Fed {} from {} with {}.",
                               new Object[] {part, feeder, nozzle});
                   });
                   break;
               }
               catch (Exception e) {
                   Logger.debug("Feed {} from {} with {} failed!",
                           new Object[] {part.getId(), feeder, nozzle});
                   if (feeder.isAutoSkipPick()){
                       makeSkip=true;
                   } 
               
               // If the feed fails, disable the feeder and continue. If there are no
               // more valid feeders the findFeeder() call above will throw and exit the
               // loop.
               feeder.setEnabled(false);
               lastErrorFeeder = feeder;
               lastError = e;
           }
        }
        
      //Logger.debug("prePickTest prePickTest whether the Part is off - nozzle empty");
      //nozzle.isPartOffTest(); //this is the procedure to check before the pick whether the nozzle is empty

               // Pick the part
        Feeder feeder = plannedPlacement.feeder;
           try {
           // Get the feeder that was used to feed
               retry(1+feeder.getPickRetryCount(), () -> {
                   fireTextStatus("Picking %s from %s for %s.", part.getId(),
                   feeder.getName(), placement.getId());
                   Logger.debug("Attempt Pick {} from {} with {}.",
                   new Object[] {part, feeder, nozzle});

           // Move to the pick location
          Logger.debug("move to the pick location");                  
          //MovableUtils.moveToLocationAtSafeZ(nozzle, feeder.getPickLocation());
          MovableUtils.moveToLocationAtSafeZ(nozzle, feeder.getPickLocation().derive(null, null, 0.0, null)); //move to pick location but don't low down nozzle at the destination
          
          Logger.debug("Is nozzle clean before picking? {}", nozzle.getName());
          fireTextStatus("Checking is nozzle clean before picking %s for %s.", part.getId(), placement.getId());
          nozzle.isPartOffTest(); //this is the procedure to check before the pick whether the nozzle is empty
          
          MovableUtils.moveToLocationAtSafeZ(nozzle, feeder.getPickLocation()); //in fact this is only a low down the nozzle
          
          // Pick
          fireTextStatus("Picking the Part %s for %s.", part.getId(), placement.getId());
          nozzle.pick(part);
          
          // Retract
          nozzle.moveToSafeZ();
          fireTextStatus("Picked %s from %s for %s.", part.getId(),
          feeder.getName(), placement.getId());
          Logger.debug("Picked {} from {} with {}", part, feeder, nozzle);
                   
          // feed after pick
          if (feeder != null) {
              feeder.postPick(nozzle);
          }
               });
           }
          catch (Exception e) {
              if (feeder.isAutoSkipPick()) { 
              makeSkip=true;
              if (isAutoDisableFeeder() && !isDisableAutomatics()) {
                  feeder.setEnabled(false);
              }
               } 
               throw (e);
           }
       }
    
    /*
     * INFO: This is modified doAlign.
     * Now if alignment fails due to lost or wrong picked part, the part is discarded and tried to 
     * subFeedAndPick pick again and then Aligned again with programmable counter (Align Retry Count).
     * (In the section above see description how operates new modified doPickAndFeed).
     * If all this fails and user will choose <try again> the part is again picked and aligned.
     */	
    protected void doAlign() throws Exception {
    	boolean atCameraPosition = false;
        for (PlannedPlacement plannedPlacement : plannedPlacements) {
            if (plannedPlacement.stepComplete) {
                continue;
            }

            Nozzle nozzle = plannedPlacement.nozzle;
            JobPlacement jobPlacement = plannedPlacement.jobPlacement;
            Placement placement = jobPlacement.placement;
            BoardLocation boardLocation = jobPlacement.boardLocation;
            Part part = placement.getPart();
            fireTextStatus("doAlign started for %s for %s.", part.getId(), placement.getId());
            
            Feeder feeder = plannedPlacement.feeder;
            
//START of New added section to check isPartOn at the cameraLocation instead of pickLocation.
//Remove whole this section if works wrong and uncomment commented related section inReferenceNozzle pick(Part part)            
            int pCounter = feeder.getPickRetryCount();
            while (true) {
                try {
                /*
                We want to move nozzle to the camera position to perform there delayed isPartOn test (not at the pick position).
                We need it only one time before the first part alignment, for the remaining parts we already don't need (long time 
                has passed from the moment of the picking procedure).
                This is the reason why "atCameraPosition" variable is created.
                */                 	
                  if (!atCameraPosition) {
                  Camera camera = VisionUtils.getBottomVisionCamera();
                  MovableUtils.moveToLocationAtSafeZ(nozzle, camera.getLocation(nozzle));
                  atCameraPosition = true;
                  }
                /*
                In fact, here we finalize picking procedure confirming whether the part was picked properly (isPartOnTest not returns exception).
                If not - we repeat the pick procedure.
                */                  
                  Logger.debug("isPartOnTest to check whether the Part is still On the nozzle");
                  fireTextStatus("Checking isPartOn over the Camera: %s for %s.", part.getId(), placement.getId());
                  nozzle.isPartOnTest();
                  break;
                }
                catch (Exception e) { 
                    if (pCounter>0) {
                    	atCameraPosition = false;
                    	pCounter--;
                    	fireTextStatus("Discarding %s from %s.", part.getId(), nozzle);
                    	discard(nozzle);
                    	fireTextStatus("Picking again %s from %s for %s.", part.getId(),
                    			feeder.getName(), placement.getId());
                    	subFeedAndPick(plannedPlacement);
                    }
                    else {
                        if (feeder.isAutoSkipPick()) { 
                            makeSkip=true;
                            if (isAutoDisableFeeder() && !isDisableAutomatics()) {
                                feeder.setEnabled(false);
                            }
                             } 
                    	throw (e);
                    }
                }
            }
//END of New added section to check isPartOn at the cameraLocation instead of pickLocation.  

            fireTextStatus("Aligning %s for %s.", part.getId(), placement.getId());
            PartAlignment partAlignment = findPartAligner(machine, part); //here we get information the vision is enabled

            int alignCount = feeder.getAlignRetryCount();
            int i=0;
            
            while(i++<=alignCount) {
                if (alignCount==0) {                                //protection against accidental possible situation: User has
                                                                    //changed feeder's settings to 0 during the doAlign repetitions 
                                                                    //performing and then bottom vision is skipped. Low chance for
                                                                    //occurrence but who knows..., better to be protected.
                    plannedPlacement.disableAlignment=false;
                }
                try {
                    if(partAlignment!=null) {
                        if (plannedPlacement.disableAlignment) {    //avoid useless aligning if pressed "try again" after the
                                                                    //picking was failed and there must be no part on the nozzle.

                            throw new Exception(); 	                //we need exception to not align but go to catch and start 
                                                                    //next part picking.
                        }
                        Logger.debug("Probe of Aligning nr: {}", i);
                        plannedPlacement.alignmentOffsets = VisionUtils.findPartAlignmentOffsets(
                            partAlignment,
                            part,
                            boardLocation,
                            placement.getLocation(), nozzle);
//                        // My customization: store the actual corrected rotation instead of the offset for shared C axis
//                        if (plannedPlacement.alignmentOffsets.getPreRotated()) {
//                            plannedPlacement.alignmentOffsets = new PartAlignment.PartAlignmentOffset(plannedPlacement.alignmentOffsets.getLocation().derive(null,null,null,nozzle.getLocation().getRotation()),true);
//                        }
//                        //                        
                            Logger.debug("Align {} with {}", part, nozzle);
                            Logger.debug("Offsets {}", plannedPlacement.alignmentOffsets);
                            break;
                    }
                    else {
                        plannedPlacement.alignmentOffsets=null;
                        Logger.debug("Not aligning {} as no compatible enabled aligners defined",part);
                        break;
                     }
                }
            
                catch (Exception e) {
                    if(i<=alignCount) {
                        fireTextStatus("Discarding %s from %s.", part.getId(), nozzle);
                        discard(nozzle);
            
                        plannedPlacement.disableAlignment=true;	    //protection: if picking-again during doAlign will fail and
                                                                    //user chooses <try again> - it will reinitialize doAlign(),
                                                                    //so we don't need to perform Aligning on empty nozzle but 
                                                                    //just start FeedAndPick procedure immediately.
            
                        fireTextStatus("Picking again %s from %s for %s.", part.getId(),
                                feeder.getName(), placement.getId());
                        subFeedAndPick(plannedPlacement);
                        plannedPlacement.disableAlignment=false;    //picking was succeeded so we need to align the part.
                    }
                    else if(alignCount!=0){
                        if (feeder.isAutoSkipAlign()) {
                            makeSkip=true;
                            if (isAutoDisableFeeder() && !isDisableAutomatics()) {
                                feeder.setEnabled(false);
                            }
                        }
                  
                    //plannedPlacement.disableAlignment = true;	    //if this is commented: after when the f dialog message is 
                                                                    //thrown, user may manually correct the part on nozzle and 
                                                                    //choose <Try Again>. The part will be tried to be aligned 
                                                                    //before trying to discard and pick the new part.
                                                                    //To consider and tests but it seems be good as it is.
                        throw new Exception(String.format(
                                "ReferenceBottomVision (%s): No result found. <Try again> to Pick and Align again.",
                                part.getId()));
                    }
                    else {
                        throw new Exception(String.format(
                            "ReferenceBottomVision (%s): No result found. <Try again> to Align again.",
                            part.getId()));
                    }
                }
            }
            plannedPlacement.stepComplete = true;
            Logger.debug("plannedPlacement.stepComplete completed <true>");
        }
        clearStepComplete();
    }

    protected void doPlace() throws Exception {
        // My customization: placement the parts in reversed sequence than picking N1N2N3>N3N2N1
        Collections.reverse(plannedPlacements);
        //    	
        for (PlannedPlacement plannedPlacement : plannedPlacements) {
            if (plannedPlacement.stepComplete) {
                continue;
            }
            Nozzle nozzle = plannedPlacement.nozzle;
            JobPlacement jobPlacement = plannedPlacement.jobPlacement;
            Placement placement = jobPlacement.placement;
            Part part = placement.getPart();
            BoardLocation boardLocation = plannedPlacement.jobPlacement.boardLocation;
            //Check if the individual piece has a fiducial check and check to see if the board is enabled
            if(jobPlacement.placement.getCheckFids()&&jobPlacement.boardLocation.isEnabled()) {
                doIndividualFiducialCheck(jobPlacement.boardLocation);
            }

            // Check if there is a fiducial override for the board location and if so, use it.
            Location placementLocation =
                    Utils2D.calculateBoardPlacementLocation(boardLocation, placement.getLocation());

            // If there are alignment offsets update the placement location with them
            if (plannedPlacement.alignmentOffsets != null) {

                /*
                 preRotated means during alignment we have already rotated the component
                 - this is useful for say an external rotating stage that the component is
                 placed on, rotated to correct placement angle, and then picked up again.
                 */
                if (plannedPlacement.alignmentOffsets.getPreRotated()) {
                    placementLocation = placementLocation.subtractWithRotation(
                            plannedPlacement.alignmentOffsets.getLocation());
//                    //My customization: restore the corrected rotation and override the offset calc for shared C axis
//                    placementLocation = placementLocation.derive(null,null,null,plannedPlacement.alignmentOffsets.getLocation().getRotation());
//                    //                    
                    }
                else {
                    Location alignmentOffsets = plannedPlacement.alignmentOffsets.getLocation();
                    // Rotate the point 0,0 using the alignment offsets as a center point by the angle
                    // that is
                    // the difference between the alignment angle and the calculated global
                    // placement angle.
                    Location location =
                            new Location(LengthUnit.Millimeters).rotateXyCenterPoint(alignmentOffsets,
                                    placementLocation.getRotation() - alignmentOffsets.getRotation());

                    // Set the angle to the difference mentioned above, aligning the part to the
                    // same angle as
                    // the placement.
                    location = location.derive(null, null, null,
                            placementLocation.getRotation() - alignmentOffsets.getRotation());

                    // Add the placement final location to move our local coordinate into global
                    // space
                    location = location.add(placementLocation);

                    // Subtract the alignment offsets to move the part to the final location,
                    // instead of
                    // the nozzle.
                    location = location.subtract(alignmentOffsets);

                    placementLocation = location;
                }
            }

            // Add the part's height to the placement location
            placementLocation = placementLocation.add(new Location(part.getHeight().getUnits(), 0,
                    0, part.getHeight().getValue(), 0));

//            try {
//                HashMap<String, Object> params = new HashMap<>();
//                params.put("job", job);
//                params.put("jobProcessor", this);
//                params.put("part", part);
//                params.put("nozzle", nozzle);
//                params.put("placement", placement);
//                params.put("boardLocation", boardLocation);
//                params.put("placementLocation", placementLocation);
//                params.put("alignmentOffsets", plannedPlacement.alignmentOffsets);
//            Configuration.get().getScripting().on("Job.Placement.BeforeAssembly", params);
//            }
//            catch (Exception e) {
//                Logger.warn(e);
//            }
            
            // Move to the placement location
            MovableUtils.moveToLocationAtSafeZ(nozzle, placementLocation);

            fireTextStatus("Placing %s for %s.", part.getId(), placement.getId());

            // Place the part
            nozzle.place();

            // Retract
            nozzle.moveToSafeZ(); //||
            
            // Mark the placement as finished
            jobPlacement.status = Status.Complete;
            
            // Mark the placement as "placed"
            boardLocation.setPlaced(jobPlacement.placement.getId(), true);
            
            ++totalPartsPlaced;

            plannedPlacement.stepComplete = true;

//            try {
//                HashMap<String, Object> params = new HashMap<>();
//                params.put("job", job);
//                params.put("jobProcessor", this);
//                params.put("part", part);
//                params.put("nozzle", nozzle);
//                params.put("placement", placement);
//                params.put("boardLocation", boardLocation);
//                params.put("placementLocation", placementLocation);
//                Configuration.get().getScripting().on("Job.Placement.Complete", params);
//            }
//            catch (Exception e) {
//                Logger.warn(e);
//            }
            
            Logger.debug("Place {} with {}", part, nozzle.getName());

            saveJobAndConfig(false);
        }

        clearStepComplete();
    }

    protected void doCleanup() throws Exception {
        fireTextStatus("Cleaning up.");

        // Safe Z the machine
        head.moveToSafeZ();
        
        // Discard any currently picked parts
        discardAll(head);

        // Safe Z the machine
        head.moveToSafeZ();

        if (parkWhenComplete) {
            fireTextStatus("Park nozzle.");
            MovableUtils.park(head);
        }
        
        double dtSec = (System.currentTimeMillis() - startTime)/1000.0;
        DecimalFormat df = new DecimalFormat("###,###.0");
        
        Logger.info("Job finished {} parts in {} sec. This is {} pph.  Skipped %s parts.", totalPartsPlaced, df.format(dtSec), df.format(totalPartsPlaced / (dtSec / 3600.0)), totalPartsSkipped);
        Logger.info("Cycles {}, Nozzle Tip Changes {}", cycles, nozzleTipChanges);
        
        HashMap<String, Object> params = new HashMap<>();
        params.put("job", job);
        params.put("jobProcessor", this);
        Configuration.get().getScripting().on("Job.Finished", params);
        
        fireTextStatus("Job finished: Placed %s parts in %s sec (%s CPH). Fixed %s parts.", totalPartsPlaced, df.format(dtSec), df.format(totalPartsPlaced / (dtSec / 3600.0)), totalPartsSkipped);
        doTopLightOn();
 
        saveJobAndConfig(true);
    }
    
    protected void doReset() throws Exception {
        this.job = null;
    }

    /**
     * Discard the picked part, if any. Remove the currently processing PlannedPlacement from the
     * list and mark the JobPlacement as Skipped.
     * 
     * @throws Exception
     */
    protected void doSkip() throws Exception {
        if (plannedPlacements.size() > 0) {
            
            // get iterator to avoid ConcurrentModificationException (list is modified within iteration)
            Iterator<PlannedPlacement> plannedPlacementIter = plannedPlacements.iterator();
            
            // iterate through planned placement in this cycle (number of planned placements ==
            // number of nozzles)
            while (plannedPlacementIter.hasNext()) {
                PlannedPlacement plannedPlacement=plannedPlacementIter.next();
                JobPlacement jobPlacement = plannedPlacement.jobPlacement;
                
                if (plannedPlacement.stepComplete) {
                    // go over placements having the current step already completed
                    continue;
                }

                // remove the placement to be skipped from list
                plannedPlacementIter.remove();

                fireTextStatus("Skipping... ");

                // discard
                Nozzle nozzle = plannedPlacement.nozzle;
                fireTextStatus("Discarding skipped part from nozzle: %s.",  nozzle.getName());
                discard(nozzle);
                Logger.debug("making discard");
                
                if(makeSkip) {
                    Placement placement = jobPlacement.placement;
                    Part part = placement.getPart();
                    HashMap<String, Object> params = new HashMap<>();
                    params.put("part", part);
                    params.put("nozzle", nozzle);
                    params.put("feeder", plannedPlacement.feeder);
                    Configuration.get().getScripting().on("Job.SkipAlarm", params); 
                                            //we can inform that something was auto Skipped 
                                            //and probably need to repair some feeder,
                                            //or fire some alarm actuator from the script 
                                            //(buzzer, light etc)
                    //++totalPartsSkipped;
                    //jobPlacement.status = Status.Skipped; //to consider whether we want to collect
                                                            //only automatically skipped parts or all
                }
                
                ++totalPartsSkipped;
                jobPlacement.status = Status.Skipped;
                Logger.debug("Skipped {}", jobPlacement.placement);
                makeSkip=false;

                // stop iterating through plannedPlacements, since only one part is handled at a time
                break;
                
            }
        }
    }
    
    /**
     * Mark the currently processing step as complete in the list of PlannedPlacement to ignore an raised error and go on assembly
     * 
     * @throws Exception
     */
    protected void doIgnoreContinue() throws Exception {
        if (plannedPlacements.size() > 0) {
            for (PlannedPlacement plannedPlacement : plannedPlacements) {
                if (plannedPlacement.stepComplete) {
                    // go over placements having the current step already completed
                    continue;
                }
                
                JobPlacement jobPlacement = plannedPlacement.jobPlacement;
                
                //mark current step as completed successfully done
                plannedPlacement.stepComplete = true;
                Logger.debug("Ignored Error and Continued for {}", jobPlacement.placement);
                
                // stop iterating through plannedPlacements since only one error is handled at a time
                break;
            }
        }
    }
 
    protected void clearStepComplete() {
        for (PlannedPlacement plannedPlacement : plannedPlacements) {
            plannedPlacement.stepComplete = false;
        }
    }

    protected List<JobPlacement> getPendingJobPlacements() {
        return this.jobPlacements.stream().filter((jobPlacement) -> {
            return jobPlacement.status == Status.Pending;
        }).collect(Collectors.toList());
    }

    protected List<JobPlacement> getSkippedJobPlacements() {
        return this.jobPlacements.stream().filter((jobPlacement) -> {
            return jobPlacement.status == Status.Skipped;
        }).collect(Collectors.toList());
    }
    
    protected boolean isJobComplete() {
        return getPendingJobPlacements().isEmpty();
    }
    
    @Override
    public Wizard getConfigurationWizard() {
        return new ReferencePnpJobProcessorConfigurationWizard(this);
    }
    
    public boolean isParkWhenComplete() {
        return parkWhenComplete;
    }

    public void setParkWhenComplete(boolean parkWhenComplete) {
        this.parkWhenComplete = parkWhenComplete;
    }
    
    public boolean isAutoSaveJob() {
        return autoSaveJob;
    }

    public void setAutoSaveJob(boolean autoSaveJob) {
        this.autoSaveJob = autoSaveJob;
    }

    public boolean isAutoSaveConfiguration() {
        return autoSaveConfiguration;
    }

    public void setAutoSaveConfiguration(boolean autoSaveConfiguration) {
        this.autoSaveConfiguration = autoSaveConfiguration;
    }
    
    public boolean isDisableAutomatics() {
        return disableAutomatics;
    }

    public void setDisableAutomatics(boolean disableAutomatics) {
        this.disableAutomatics = disableAutomatics;
    }  
    
    public boolean isAutoSkipDisabledFeeders() {
        return autoSkipDisabledFeeders;
    }

    public void setAutoSkipDisabledFeeders(boolean autoSkipDisabledFeeders) {
        this.autoSkipDisabledFeeders = autoSkipDisabledFeeders;
    }  
    
    public boolean isAutoDisableFeeder() {
        return autoDisableFeeder;
    }

    public void setAutoDisableFeeder(boolean autoDisableFeeder) {
        this.autoDisableFeeder = autoDisableFeeder;
    }  

    public boolean isDisableTipChanging() {
        return disableTipChanging;
    }

    public void setDisableTipChanging(boolean disableTipChanging) {
        ReferencePnpJobProcessor.disableTipChanging = disableTipChanging;
    }
    
//    public int getSizeThreshold() {
//        return sizeThreshold;
//    }
//  
//    public void setSizeThreshold(int sizeThreshold) {
//        ReferencePnpJobProcessor.sizeThreshold = sizeThreshold;
//    }

    public long getConfigSaveFrequencyMs() {
        return configSaveFrequencyMs;
    }
    
    public JobOrderHint getJobOrder() {
        return jobOrder;
    }
    
    public void setJobOrder(JobOrderHint newJobOrder) {
        this.jobOrder = newJobOrder;
    }    

    private void saveJobAndConfig(boolean ignoreTimer) throws Exception {
        Logger.debug("saveJobAndConfig({})", ignoreTimer);
        if (autoSaveJob) {
            Logger.debug("Auto saving job.");
            File file = job.getFile();
            if (file != null) {
                Configuration.get().saveJob(job, file);
            }
        }
        if (autoSaveConfiguration && (ignoreTimer || System.currentTimeMillis() > lastConfigSavedTimeMs + configSaveFrequencyMs)) {
            Logger.debug("Auto saving config.");
            Configuration.get().save();
            lastConfigSavedTimeMs = System.currentTimeMillis();
        }
    }
    
    @Root
    public static class StandardPnpJobPlanner implements PnpJobPlanner {
        Head head;
        
        public List<JobPlacement> plan(Head head, List<JobPlacement> jobPlacements) {
            this.head = head;
            
            // Create a List of Lists of JobPlacements that each Nozzle can handle, including
            // one instance of null per Nozzle. The null indicates a possible "no solution"
            // for that Nozzle.
            List<List<JobPlacement>> solutions = head.getNozzles().stream().map(nozzle -> {
                return Stream.concat(jobPlacements.stream().filter(jobPlacement -> {
                    return nozzleCanHandle(nozzle, jobPlacement.placement.getPart());
                }), Stream.of((JobPlacement) null)).collect(Collectors.toList());
            }).collect(Collectors.toList());

            // Get the cartesian product of those Lists
            List<JobPlacement> result = Collect.cartesianProduct(solutions).stream()
                    // Filter out any results that contains the same JobPlacement more than once
                    .filter(list -> {
                        // Note: A previous version of this code just dumped everything into a
                        // set and compared the size. This worked for two nozzles since there would
                        // never be more than two nulls, but for > 2 nozzles there will always be a
                        // solution that has > 2 nulls, which means the size will never match.
                        // This version of the code ignores the nulls (since they are valid
                        // solutions) and instead only checks for duplicate valid JobPlacements.
                        // There is probably a more clever way to do this, but it isn't coming
                        // to me at the moment.
                        HashSet<JobPlacement> set = new HashSet<>();
                        for (JobPlacement jp : list) {
                            if (jp == null) {
                                continue;
                            }
                            if (set.contains(jp)) {
                                return false;
                            }
                            set.add(jp);
                        }
                        return true;
                    })
                    // Sort by the solutions that contain the fewest nulls followed by the
                    // solutions that require the fewest nozzle changes.
                    .sorted(byFewestNulls.thenComparing(byFewestNozzleChanges))
                    // And return the top result.
                    .findFirst().orElse(null);
            return result;
        }
        
        // Sort a List<JobPlacement> by the number of nulls it contains in ascending order.
        Comparator<List<JobPlacement>> byFewestNulls = (a, b) -> {
            return Collections.frequency(a, null) - Collections.frequency(b, null);
        };

        // Sort a List<JobPlacement> by the number of nozzle changes it will require in
        // descending order.
        Comparator<List<JobPlacement>> byFewestNozzleChanges = (a, b) -> {
            int countA = 0, countB = 0;
            for (int i = 0; i < head.getNozzles().size(); i++) {
                Nozzle nozzle = head.getNozzles().get(i);
                JobPlacement jpA = a.get(i);
                JobPlacement jpB = b.get(i);
                if (nozzle.getNozzleTip() == null) {
                    countA++;
                    countB++;
                    continue;
                }
                if (jpA != null && !nozzle.getNozzleTip().canHandle(jpA.placement.getPart())) {
                    countA++;
                }
                if (jpB != null && !nozzle.getNozzleTip().canHandle(jpB.placement.getPart())) {
                    countB++;
                }
            }
            return countA - countB;
        };
    }

    @Root
    public static class SimplePnpJobPlanner implements PnpJobPlanner {
        /**
         * This is a trivial planner that does not try very hard to make an optimized job, but also
         * does not fail on large jobs like the Standard one does.
         * 
         * - For each planning cycle, the planner loops through each nozzle on the head. 
         * - For each nozzle it then loops through the list of remaining placements and
         *   finds the first placement that does not require a nozzle tip change. 
         * - If none are found it next searches for a placement that can be handled with a nozzle
         *   tip change.
         * - If no compatible placement is found in the searches above the nozzle is left empty.
         */
        @Override
        public List<JobPlacement> plan(Head head, List<JobPlacement> jobPlacements) {
            // Sort the placements by number of compatible nozzles ascending. This causes the
            // planner to prefer plans that have greater nozzle diversity, leading to overall
            // better nozzle usage as fewer placements remain.
//            jobPlacements.sort(new Comparator<JobPlacement>() {
//                @Override
//                public int compare(JobPlacement o1, JobPlacement o2) {
//                    int c1 = 0;
//                    for (Nozzle nozzle : head.getNozzles()) {
//                        if (AbstractPnpJobProcessor.nozzleCanHandle(nozzle, o1.placement.getPart())) {
//                            c1++;
//                        }
//                    }
//                    int c2 = 0;
//                    for (Nozzle nozzle : head.getNozzles()) {
//                        if (AbstractPnpJobProcessor.nozzleCanHandle(nozzle, o2.placement.getPart())) {
//                            c2++;
//                        }
//                    }
//                    return c1 - c2;
//                }
//            });
            List<JobPlacement> result = new ArrayList<>();
            for (Nozzle nozzle : head.getNozzles()) {
                JobPlacement solution = null;
                
                // First, see if we can put a placement on the nozzle that will not require a
                // nozzle tip change.
                if (nozzle.getNozzleTip() != null) {
                    for (JobPlacement jobPlacement : jobPlacements) {
                    	Placement placement = jobPlacement.placement;
                    	Part part = placement.getPart();
//                    	double height = placement.getPart().getHeight().getValue();
//                    	if (height >= 4.0) {};
                    	if (nozzle.getNozzleTip().canHandle(part)) {
                            solution = jobPlacement;
                            break;
                        }
                    }
                }
                if (solution != null) {
                    jobPlacements.remove(solution);
                    result.add(solution);
                    continue;
                }
                
                // If that didn't work, see if we can put one on with a nozzle tip change.
                for (JobPlacement jobPlacement : jobPlacements) {
                    Placement placement = jobPlacement.placement;
                    //int size = jobPlacements.size();
                    Part part = placement.getPart();
                    //if (nozzleCanHandle(nozzle, part) && (disableTipChanging==false || size>sizeThreshold)) { //don't use isDisableTipChanging()
                    if (nozzleCanHandle(nozzle, part) && (disableTipChanging==false)) { //don't use isDisableTipChanging()	
                        solution = jobPlacement;
                        break;
                    }
                }

                if (solution != null) {
                    jobPlacements.remove(solution);
                    result.add(solution);
                    continue;
                }
                
                // And if that didn't work we give up on this nozzle.
                result.add(null);
            }
            return result;
        }
    }
    
    /**
     * A very simple planner that processes the job placements in the other they are specified
     * and does not support nozzle tip changes. The planner will return placements that work
     * with the loaded nozzle tips until none are left, and then the job will end.
     */
//    @Root
//    public static class TrivialPnpJobPlanner implements PnpJobPlanner {
//        @Override
//        public List<JobPlacement> plan(Head head, List<JobPlacement> jobPlacements) {
//            /**
//             * Create a List<PlannedPlacement> that we will fill up and then return.
//             */
//            List<JobPlacement> plannedPlacements = new ArrayList<>();
//            
//            /**
//             * Loop over each nozzle in the head and assign a placement to it.
//             */
//            for (Nozzle nozzle : head.getNozzles()) {
//                /**
//                 * If the nozzle does not have a nozzle tip attached then we won't process it. We
//                 * could choose to specify a nozzle tip change, but for the purpose of this simple
//                 * example we assume the user only wants to process using the currently loaded
//                 * nozzle tips.
//                 */
//                if (nozzle.getNozzleTip() == null) {
//                    continue;
//                }
//                
//                /**
//                 * If there are no more placements to process then we're done, so exit the loop.
//                 */
//                if (jobPlacements.isEmpty()) {
//                    break;
//                }
//                
//                /**
//                 * Loop through the remaining job placements and find the first one that is
//                 * compatible with the nozzle and nozzle tip. Note that we use an Iterator here,
//                 * instead of the normal for each loop. The reason is that we need to remove
//                 * the job placement later in the loop, and Java does not support removing an
//                 * item from a list while it's being stepped through. The iterator has a special
//                 * method of Iterator.remove() which allows this.
//                 */
//                for (Iterator<JobPlacement> iterator = jobPlacements.iterator(); iterator.hasNext(); ) {
//                    /**
//                     * Get the next JobPlacement from the Iterator.
//                     */
//                    JobPlacement jobPlacement = iterator.next();
//                    
//                    /**
//                     * Assign some local temporary variables to make the code below easier to read. 
//                     */
//                    //Placement placement = jobPlacement.getPlacement();
//                    Placement placement = jobPlacement.placement;
//                    Part part = placement.getPart();
//                    org.openpnp.model.Package packag = part.getPackage();
//                    NozzleTip nozzleTip = nozzle.getNozzleTip();
//                    
//                    /**
//                     * Check if the job placemen't package is compatible with the nozzle tip
//                     * attached to this nozzle.
//                     */
//                    if (packag.getCompatibleNozzleTips().contains(nozzleTip)) {
//                        /**
//                         * It's compatible, so create a PlannedPlacement which is a holder for a 
//                         * nozzle, nozzle tip and a job placement.
//                         */
//                        PlannedPlacement plannedPlacement = new PlannedPlacement(nozzle, nozzle.getNozzleTip(), jobPlacement);
//                        
//                        /**
//                         * Store it in the results.
//                         */
//                        plannedPlacements.add(plannedPlacement);
//                        
//                        /**
//                         * And remove the job placement from the list. This ensures we don't process
//                         * the same one again later.
//                         */
//                        iterator.remove();
//                        
//                        /**
//                         * And exit the loop, because we are done with this nozzle.
//                         */
//                        break;
//                    }
//                }
//            }
//            
//            /**
//             * Return the results
//             */
//            return plannedPlacements;
//        }
//    }       
}