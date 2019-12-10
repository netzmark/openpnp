package org.openpnp.machine.reference;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;

import org.openpnp.ConfigurationListener;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.gui.support.Wizard;
import org.openpnp.machine.reference.psh.NozzleTipsPropertySheetHolder;
import org.openpnp.machine.reference.wizards.ReferenceNozzleCameraOffsetWizard;
import org.openpnp.machine.reference.wizards.ReferenceNozzleConfigurationWizard;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Camera;
import org.openpnp.spi.JobProcessor;//
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.spi.base.AbstractNozzle;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.VisionUtils;
import org.pmw.tinylog.Logger;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;

public class ReferenceNozzle extends AbstractNozzle implements ReferenceHeadMountable {
    @Element
    private Location headOffsets = new Location(LengthUnit.Millimeters);

    @Attribute(required = false)
    private int pickDwellMilliseconds;

    @Attribute(required = false)
    private int placeDwellMilliseconds;

    @Attribute(required = false)
    private String currentNozzleTipId;

    @Attribute(required = false)
    private boolean changerEnabled = false;

    @Element(required = false)
    protected Length safeZ = new Length(0, LengthUnit.Millimeters);

    @Element(required = false)
    protected String vacuumSenseActuatorName;

    @Attribute(required = false)
    protected boolean invertVacuumSenseLogic;
    
    /**
     * If limitRotation is enabled the nozzle will reverse directions when commanded to rotate past
     * 180 degrees. So, 190 degrees becomes -170 and -190 becomes 170.
     */
    @Attribute(required = false)
    private boolean limitRotation = true;

    protected ReferenceNozzleTip nozzleTip;

    Actuator actVacuum; // Marek change: machine is faster, but need program restart if name changes. vacuum actuator
    public ReferenceNozzle() {
        Configuration.get().addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationLoaded(Configuration configuration) throws Exception {
                nozzleTip = (ReferenceNozzleTip) nozzleTips.get(currentNozzleTipId);
                actDown = getHead().getMachine().getActuator(getId()); // Marek change: actuator added to can control lower/raise Nozzle from RefferenceNozzle
                actVacuum = getHead().getMachine().getActuator(getId()+"_VAC"); // Marek change: actuator added to can control vacuum on/off from RefferenceNozzle

            }
        });
    }
   
    public ReferenceNozzle(String id) {
        this();
        this.id = id;
    }
    
    public boolean isLimitRotation() {
        return limitRotation;
    }

    public void setLimitRotation(boolean limitRotation) {
        this.limitRotation = limitRotation;
    }

    public int getPickDwellMilliseconds() {
        return pickDwellMilliseconds;
    }

    public void setPickDwellMilliseconds(int pickDwellMilliseconds) {
        this.pickDwellMilliseconds = pickDwellMilliseconds;
    }

    public int getPlaceDwellMilliseconds() {
        return placeDwellMilliseconds;
    }

    public void setPlaceDwellMilliseconds(int placeDwellMilliseconds) {
        this.placeDwellMilliseconds = placeDwellMilliseconds;
    }
    
    @Override
    public Location getHeadOffsets() {
        return headOffsets;
    }

    @Override
    public void setHeadOffsets(Location headOffsets) {
        this.headOffsets = headOffsets;
        // Changing a head offset invalidates the nozzle tip calibration.
        ReferenceNozzleTip.Calibration.resetAllNozzleTips();
    }

    public String getVacuumSenseActuatorName() {
        return vacuumSenseActuatorName;
    }

    public void setVacuumSenseActuatorName(String vacuumSenseActuatorName) {
        this.vacuumSenseActuatorName = vacuumSenseActuatorName;
    }

    public boolean isInvertVacuumSenseLogic() {
        return invertVacuumSenseLogic;
    }

    public void setInvertVacuumSenseLogic(boolean invertVacuumSenseLogic) {
        this.invertVacuumSenseLogic = invertVacuumSenseLogic;
    }

    @Override
    public ReferenceNozzleTip getNozzleTip() {
        return nozzleTip;
    }
    
    @Override
    public void prePickTest() throws Exception { //Marek: this is the procedure to check vacuum before the pick wether the nozzle is empty
        
        Actuator actuator = getHead().getActuatorByName(vacuumSenseActuatorName);
        if (actuator != null) {
            ReferenceNozzleTip nt = getNozzleTip();
            double vacuumLevel = Double.parseDouble(actuator.read());
            if (invertVacuumSenseLogic) {
                if (vacuumLevel < (nt.getVacuumLevelPartOff()-50)) { //50 is offset, if it is >(Off-offset) means nozzle is not empty before pick
                    throw new Exception(String.format(
                        "Prepick test failure: Vacuum level %f is lower than expected value of %f for part off. Part may be stuck to nozzle.",
                        vacuumLevel, nt.getVacuumLevelPartOff()));
                }
            }
            else {
                if (vacuumLevel > (nt.getVacuumLevelPartOff()+50)) { //50 is offset, if it is >(Off+offset) means nozzle is not empty before pick
                    throw new Exception(String.format(
                        "Prepick test failure: Vacuum level %f is higher than expected value of %f (+50) for part off. Part may be stuck to nozzle.",
                        vacuumLevel, nt.getVacuumLevelPartOff()));
                }
            }
        }
    }

    
    @Override
    //Idea of operation is:
    //- Zdown actuated from JobProcessor; I have added actDown new actuator to RefferenceNozzle to lower/raise my pneumatic nozzle
    //- PumpOn in PICK_COMMAND or even not needed tu do it here if vacumm ramained on nozzle after 
    //Placement - as I do having blowing nozzle venturies where vacuum better to have turned ON
    //before feeder position achieved because pressure blowout the parts from the tape.
    //- pickDwellTime; I don't need it here but others may need.
    //- first vacuum check to detect "VacuumLevelPartOn" level; to lift nozzle up asap; option: vacuum level tracking
    //- Zup; I do it with my new actDown actuator. For those who have typical Z-motor control you need to 
    //implement it somwhow - out of range of my skills :-(. Or just put proper gCode into an actuator maybe.
    //- pickDwellTime; I personaly prefer have it here
    //- second vacuum check to detect "VacuumLevelPartOn" level; to confirm the part is really raised up
    //- end

    public void pick(Part part) throws Exception {
        Logger.debug("{}.pick()", getName());
        if (part == null) {
            throw new Exception("Can't pick null part");
        }
        if (nozzleTip == null) {
            throw new Exception("Can't pick, no nozzle tip loaded");
        }
        this.part = part;
        getDriver().pick(this);
        getMachine().fireMachineHeadActivity(head);

        //actDown.actuate(true); //lowering nozzle before makePick. Removed because the system made it automatically before

        Actuator actuator = getHead().getActuatorByName(vacuumSenseActuatorName);

        if (actuator != null) { 
            int times=10;
            ReferenceNozzleTip nt = getNozzleTip();
            double vacuumLevel;

    //First vacuum check (before nozzle rising) to rise it up immediately ----
    //(vacuum value is rising after the nozzle touched to the part, so we track the rising to avoid hard dwell time counting) 
            while(times-->0) {
                vacuumLevel = Double.parseDouble(actuator.read());
            
                if (invertVacuumSenseLogic) {
                    if (vacuumLevel > nt.getVacuumLevelPartOn()) {
                        Thread.sleep(10);
                    }
                else { break; }
                }
                else {
                    if (vacuumLevel < nt.getVacuumLevelPartOn()) {
                        Thread.sleep(10);
                    }
                    else { break; }
                }
            };

            if(actDown!=null) {
                Logger.debug("{}.moveTo(Nozzle Up)", getId());
                actDown.actuate(false); //rising nozzle immediately after the VacuumLevelPartOn value detected or full loop finished
                Logger.debug("pickDwellTime after nozzle raising: {}ms", (getPickDwellMilliseconds() + nozzleTip.getPickDwellMilliseconds()));
                //Thread.sleep(this.getPickDwellMilliseconds() + nozzleTip.getPickDwellMilliseconds());
            }

    // Second vacuum check (after nozzle rising)
            vacuumLevel = (Double.parseDouble(actuator.read()) -5);
            if (invertVacuumSenseLogic) {
                if (vacuumLevel > nt.getVacuumLevelPartOn()) {
                    throw new Exception(String.format(
                        "Pick failure: Vacuum level %f is higher than expected value of %f for part on. Part may have failed to pick or feeder is empty.",
                            vacuumLevel, nt.getVacuumLevelPartOn()));
                }
            }
            else {
              if (vacuumLevel < nt.getVacuumLevelPartOn()) {
              throw new Exception(String.format(
                  "Pick failure: Vacuum level %f (incl. -5 offset) is lower than expected value of %f for part on. Part may have failed to pick or feeder is empty.",
                   vacuumLevel, nt.getVacuumLevelPartOn()));
              }
      
          	double vacuumLevel2;
            Thread.sleep(nozzleTip.getPickDwellMilliseconds());
  			Logger.debug("programmable pause: {}", nozzleTip.getPickDwellMilliseconds());
     			
           	vacuumLevel2 = Double.parseDouble(actuator.read());	
            	
              if (vacuumLevel2 >= vacuumLevel) {
                  Logger.debug("Vacuum level is {} and it is more than VacuumLevelPartOn {} - it means the part is picked successfully", vacuumLevel, nt.getVacuumLevelPartOn());
              }
              else {
            	  throw new Exception(String.format(
            			  "Pick failure: Vacuum level2 %f is lower than Vacuum level %f measured after raising. Part may have failed to pick or feeder is empty.",
            			  vacuumLevel2, vacuumLevel));
              	}
            }
        }
    }

    @Override
    //Idea of operation is:
    //- Zdown actuated from JobProcessor; I have added actDown new actuator to RefferenceNozzle lower/raise my pneumatic nozzle.
    //- PumpOFF in PLACE_COMMAND;
    //- placeDwellTime; I don't need it here but others may need. 
    //- first vacuum check to detect "VacuumLevelPartOff" level; to can lift the nozzle up asap. Optional with level tracking.
    //- Zup; I do it with my new actDown actuator. For those who have typical Z-motor control you need to 
    //implement it somwhow - out of range of my skills :-(. Or just put proper gCode into an actuator maybe.
    //- actVacuum new actuator added to turn PumpON again before second vacuum check
    //- placeDwellTime; I personaly prefer have it here
    //- second vacuum check to detect "VacuumLevelPartOff" level; to confirm the part is really released. Optional with level tracking.
    //- optional: actVacuum new actuator added to turn PumpOFF; I don't do it because need remain vacuum on empty nozzle.
    //- end
    // Warning: would be usable to have also single check before first Zdown but I don't know how to do it here in RefferenceNozzle.
        
    public void place() throws Exception {
        Logger.debug("{}.place()", getName());
        if (nozzleTip == null) {
            throw new Exception("Can't place, no nozzle tip loaded");
        }
        getDriver().place(this);
        this.part = null;
        getMachine().fireMachineHeadActivity(head);
        //Thread.sleep(placeDwellMilliseconds); //it's original place of this dwell but I don't need it here
        Actuator actuator = getHead().getActuatorByName(vacuumSenseActuatorName);

        if (actuator != null) { 
            int times=25;
            ReferenceNozzleTip nt = getNozzleTip();
            double vacuumLevel;
    //---- in Place, First vacuum check (after pumpOFF placed in PLACE_COMMAND (before nozzle rising)) ----

    //vacuum value tracking first loop start, remove this block if don't want that kind of tracking  
   	        while(times-->0) {
            vacuumLevel = Double.parseDouble(actuator.read());

        if (invertVacuumSenseLogic) {
            if (vacuumLevel < nt.getVacuumLevelPartOff()) {
                Thread.sleep(10); //was 20ms 
            }
            else { break; }
            }
        else {
            if (vacuumLevel > nt.getVacuumLevelPartOff()) {
                Thread.sleep(10); //was 20ms 
            }
            else { break; }
        }
           };
    //vacuum sequence monitoring first loop ended

            if(actDown!=null) {
                Logger.debug("{}.moveTo(Nozzle Up)", getId());
                actDown.actuate(false); //rising nozzle immediately after the VacuumLevelPartOff value detected or full loop finished
                Logger.debug("placeDwellTime between checks: {}ms", (getPlaceDwellMilliseconds() + nozzleTip.getPlaceDwellMilliseconds()));
                Thread.sleep(this.getPlaceDwellMilliseconds() + nozzleTip.getPlaceDwellMilliseconds());
                actVacuum.actuate(true); //pumpON to check in later procedure (prePickTest) if the part has not stayed on nozzle UWAGA MOZE DO SKRYPTU
            }

    // Second vacuum check (after nozzle rising), single check is enough to confirm whether the part is not glued)
            /*
            vacuumLevel = Double.parseDouble(actuator.read());
            if (invertVacuumSenseLogic) {
                if (vacuumLevel < nt.getVacuumLevelPartOff()) {
                    throw new Exception(String.format(
                            "Place failure: Vacuum level %f is lower than expected value of %f for part off. Part may be stuck to nozzle.",
                            vacuumLevel, nt.getVacuumLevelPartOff()));
                }
            }
            else {
                if (vacuumLevel > nt.getVacuumLevelPartOff()) {
                    throw new Exception(String.format(
                            "Place failure: Vacuum level %f is higher than expected value of %f for part off. Part may be stuck to nozzle.",
                            vacuumLevel, nt.getVacuumLevelPartOff()));
                }
            }
             */   
        }
    }
    
    private ReferenceNozzleTip getUnloadedNozzleTipStandin() {
        for (NozzleTip nozzleTip : this.getNozzleTips()) {
            if (nozzleTip instanceof ReferenceNozzleTip) {
                ReferenceNozzleTip referenceNozzleTip = (ReferenceNozzleTip)nozzleTip;
                if (referenceNozzleTip.isUnloadedNozzleTipStandin()) {
                    return referenceNozzleTip;
                }
            }
        }
        return null;
    }
    
    public ReferenceNozzleTip getCalibrationNozzleTip() {
        if (nozzleTip != null) {
            // normally we have the loaded nozzle tip as the calibration nozzle tip
            ReferenceNozzleTip calibrationNozzleTip = null;
            if (nozzleTip instanceof ReferenceNozzleTip) {
                calibrationNozzleTip = (ReferenceNozzleTip)nozzleTip;
            }
            return calibrationNozzleTip;
        } else {
            // if no tip is mounted, we use the "unloaded" nozzle tip stand-in, so we 
            // can still calibrate
            return getUnloadedNozzleTipStandin();
        }
    }
    
    @Override
    public Location getCameraToolCalibratedOffset(Camera camera) {
        // Apply the axis offset from runout calibration here. 
        ReferenceNozzleTip calibrationNozzleTip = getCalibrationNozzleTip();
        if (calibrationNozzleTip != null && calibrationNozzleTip.getCalibration().isCalibrated()) {
            return calibrationNozzleTip.getCalibration().getCalibratedCameraOffset(camera);
        }

        return new Location(camera.getUnitsPerPixel().getUnits());
    }

    Actuator actDown; // Marek change: line added by Cri  /////consider if still needed it here
    @Override
    public void moveTo(Location location, double speed) throws Exception {
        // Shortcut Double.NaN. Sending Double.NaN in a Location is an old API that should no
        // longer be used. It will be removed eventually:
        // https://github.com/openpnp/openpnp/issues/255
        // In the mean time, since Double.NaN would cause a problem for calibration, we shortcut
        // it here by replacing any NaN values with the current value from the driver.
        Location loc=location.derive(null,null,null,null);
        location = location.convertToUnits(LengthUnit.Millimeters);
        Location currentLocation = getLocation().convertToUnits(location.getUnits());
        
        if (Double.isNaN(location.getX())) {
            location = location.derive(currentLocation.getX(), null, null, null);
        }
        if (Double.isNaN(location.getY())) {
            location = location.derive(null, currentLocation.getY(), null, null);
        }
        if (Double.isNaN(location.getZ())) {
            location = location.derive(null, null, currentLocation.getZ(), null);
        }
        if (Double.isNaN(location.getRotation())) {
            location = location.derive(null, null, null, currentLocation.getRotation());
        }

        if (limitRotation && !Double.isNaN(location.getRotation())
                && Math.abs(location.getRotation()) > 180) {
            if (location.getRotation() < 0) {
                location = location.derive(null, null, null, location.getRotation() + 360);
            }
            else {
                location = location.derive(null, null, null, location.getRotation() - 360);
            }
        }

        ReferenceNozzleTip calibrationNozzleTip = getCalibrationNozzleTip();
        if (calibrationNozzleTip != null && calibrationNozzleTip.getCalibration().isCalibrated()) {
            Location correctionOffset = calibrationNozzleTip.getCalibration().getCalibratedOffset(location.getRotation());
            location = location.subtract(correctionOffset);
            Logger.debug("{}.moveTo({}, {}) (runout compensation: {})", getName(), location, speed, correctionOffset);
        } else {
            Logger.debug("{}.moveTo({}, {})", getName(), location, speed);
        }

        // inhibit motion if no change. //Marek change: to inhibit unnecessary move repetition
        int n=0;
        if (location.getX()==currentLocation.getX()) { n++;
            location = location.derive(Double.NaN, null, null, null);
        }
        if (location.getY()==currentLocation.getY()) { n++;
            location = location.derive(null,Double.NaN, null, null);
        }
        if (location.getZ()==currentLocation.getZ()) { n++;
            location = location.derive(null,null,Double.NaN, null);
        }
        if (location.getRotation()==currentLocation.getRotation()) { n++;
            location = location.derive(null,null,null,Double.NaN);
        }
        if(n==4) { return; }

        //Marek change: section below is to fire actuator for pneumatic head control in relation to Z location (change at -2)
        if(actDown!=null) {
        	if(location.getZ()<-2 && currentLocation.getZ() >=-2) {
                Logger.debug("{}.moveTo(Nozzle Down)", getId());
                actDown.actuate(true);
        	}
            if(location.getZ()>=-2 && currentLocation.getZ() <-2) {
                Logger.debug("{}.moveTo(Nozzle Up)", getId());
                actDown.actuate(false);
            }
        }

    // avoid bug inside Gcode driver
        if (Double.isNaN(location.getX())) {
            location = location.derive(currentLocation.getX(), null, null, null);
        }
        if (Double.isNaN(location.getY())) {
            location = location.derive(null, currentLocation.getY(), null, null);
        }
        if (Double.isNaN(location.getZ())) {
            location = location.derive(null, null, currentLocation.getZ(), null);
        }
        if (Double.isNaN(location.getRotation())) {
            location = location.derive(null, null, null, currentLocation.getRotation());
        }
        location = location.convertToUnits(loc.getUnits());	// convert units back;
    // ending of addition

        ((ReferenceHead) getHead()).moveTo(this, location, getHead().getMaxPartSpeed() * speed);
        getMachine().fireMachineHeadActivity(head);
    }

    @Override
    public void moveToSafeZ(double speed) throws Exception {
        Logger.debug("{}.moveToSafeZ({})", getName(), speed);
        Length safeZ = this.safeZ.convertToUnits(getLocation().getUnits());
        Location l = new Location(getLocation().getUnits(), Double.NaN, Double.NaN,
                safeZ.getValue(), Double.NaN);
        //getDriver().moveTo(this, l, getHead().getMaxPartSpeed() * speed); //Marek change
        //getMachine().fireMachineHeadActivity(head); //Marek change
        moveTo(l); // Marek change: added by Cri instead of above two lines
    }

    @Override
    public void home() throws Exception {
        Logger.debug("{}.home()", getName());
        for (NozzleTip attachedNozzleTip : this.getNozzleTips()) {
            if (attachedNozzleTip instanceof ReferenceNozzleTip) {
                ReferenceNozzleTip calibrationNozzleTip = (ReferenceNozzleTip)attachedNozzleTip;
                if (calibrationNozzleTip.getCalibration().isRecalibrateOnHomeNeeded()) {
                    if (calibrationNozzleTip == this.nozzleTip) {
                        // The currently mounted nozzle tip.
                        Logger.debug("{}.home() nozzle tip {} calibration neeeded", getName(), calibrationNozzleTip.getName());
                        calibrationNozzleTip.getCalibration().calibrate(calibrationNozzleTip, true, false);
                    }
                    else {
                        // Not currently mounted so just reset.
                        Logger.debug("{}.home() nozzle tip {} calibration reset", getName(), calibrationNozzleTip.getName());
                        calibrationNozzleTip.getCalibration().reset();
                    }
                }
            }
        }
    }
    
    @Override
    public void loadNozzleTip(NozzleTip nozzleTip) throws Exception {
        if (this.nozzleTip == nozzleTip) {
            return;
        }

        ReferenceNozzleTip nt = (ReferenceNozzleTip) nozzleTip;

        double speed = getHead().getMachine().getSpeed();
        unloadNozzleTip();

        Logger.debug("{}.loadNozzleTip({}): Start", getName(), nozzleTip.getName());
        if (changerEnabled) {
                Logger.debug("{}.loadNozzleTip({}): moveTo Start Location",
                        new Object[] {getName(), nozzleTip.getName()});
                MovableUtils.moveToLocationAtSafeZ(this, nt.getChangerStartLocation(), speed);

                Logger.debug("{}.loadNozzleTip({}): moveTo Mid Location",
                        new Object[] {getName(), nozzleTip.getName()});
                moveTo(nt.getChangerMidLocation(), nt.getChangerStartToMidSpeed() * speed);
								 
                Logger.debug("{}.loadNozzleTip({}): moveTo Mid Location 2",
                        new Object[] {getName(), nozzleTip.getName()});
                moveTo(nt.getChangerMidLocation2(), nt.getChangerMidToMid2Speed() * speed);
            }
        
        if (!nt.isUnloadedNozzleTipStandin()) { //if name is not "unmounted"
               Logger.debug("{}.loadNozzleTip({}): moveTo End Location",
                        new Object[] {getName(), nozzleTip.getName()});
               moveTo(nt.getChangerEndLocation(), nt.getChangerMid2ToEndSpeed() * speed);
               moveToSafeZ(getHead().getMachine().getSpeed());
        }
               Logger.debug("{}.loadNozzleTip({}): Finished",
                       new Object[] {getName(), nozzleTip.getName()});

            try {
                Map<String, Object> globals = new HashMap<>();
                globals.put("head", getHead());
                globals.put("nozzle", this);
                Configuration.get()
                .getScripting()
                .on("NozzleTip.Loaded", globals);
            }
            catch (Exception e) {
                Logger.warn(e);
            }
        
        this.nozzleTip = nt;
        currentNozzleTipId = nozzleTip.getId();
        if (this.nozzleTip.getCalibration().isRecalibrateOnNozzleTipChangeNeeded()) {
            Logger.debug("{}.loadNozzleTip() nozzle tip {} calibration needed", getName(), this.nozzleTip.getName());
            this.nozzleTip.getCalibration().calibrate(this.nozzleTip);
        }
        else if (this.nozzleTip.getCalibration().isRecalibrateOnNozzleTipChangeInJobNeeded()) {
            Logger.debug("{}.loadNozzleTip() nozzle tip {} calibration reset", getName(), this.nozzleTip.getName());
            // is will be recalibrated by the job - just reset() for now
            this.nozzleTip.getCalibration().reset();
        }
        firePropertyChange("nozzleTip", null, getNozzleTip());
        ((ReferenceMachine) head.getMachine()).fireMachineHeadActivity(head);
    }

    @Override
    public void unloadNozzleTip() throws Exception {
        if (nozzleTip == null) {
            return;
        }

        double speed = getHead().getMachine().getSpeed();
        
        Logger.debug("{}.unloadNozzleTip(): Start", getName());
        ReferenceNozzleTip nt = (ReferenceNozzleTip) nozzleTip;

       if (!nt.isUnloadedNozzleTipStandin()) { //if name is not "unmounted"
            Logger.debug("{}.unloadNozzleTip(): moveTo End Location", getName());
            MovableUtils.moveToLocationAtSafeZ(this, nt.getChangerEndLocation(), speed);
        }

       Logger.debug("{}.unloadNozzleTip(): moveTo Mid Location 2", getName());
       moveTo(nt.getChangerMidLocation2(), nt.getChangerMid2ToEndSpeed() * speed);

       if (changerEnabled) {
    	   if (!nt.isUnloadedNozzleTipStandin()) {								
                Logger.debug("{}.unloadNozzleTip(): moveTo Mid Location", getName());
                moveTo(nt.getChangerMidLocation(), nt.getChangerMidToMid2Speed() * speed);

                Logger.debug("{}.unloadNozzleTip(): moveTo Start Location", getName());
                moveTo(nt.getChangerStartLocation(), nt.getChangerStartToMidSpeed() * speed);
                moveToSafeZ(getHead().getMachine().getSpeed());
           }
       }
            Logger.debug("{}.unloadNozzleTip(): Finished", getName());            

            try {
                Map<String, Object> globals = new HashMap<>();
                globals.put("head", getHead());
                globals.put("nozzle", this);
                Configuration.get()
                .getScripting()
                .on("NozzleTip.Unloaded", globals);
            }
            catch (Exception e) {
                Logger.warn(e);
            }

        nozzleTip = null;
        currentNozzleTipId = null;
        firePropertyChange("nozzleTip", null, getNozzleTip());
        ((ReferenceMachine) head.getMachine()).fireMachineHeadActivity(head);

        if (!changerEnabled) {
            throw new Exception("Manual NozzleTip change required!");
        }
        // May need to calibrate the "unloaded" nozzle tip stand-in i.e. the naked nozzle tip holder. 
        ReferenceNozzleTip calibrationNozzleTip = this.getCalibrationNozzleTip();
        if (calibrationNozzleTip != null && calibrationNozzleTip.getCalibration().isRecalibrateOnNozzleTipChangeNeeded()) {
            Logger.debug("{}.unloadNozzleTip() nozzle tip {} calibration needed", getName(), calibrationNozzleTip.getName());
            calibrationNozzleTip.getCalibration().calibrate(calibrationNozzleTip);
        }
    }

    @Override
    public Location getLocation() {
        Location location = getDriver().getLocation(this);
        ReferenceNozzleTip calibrationNozzleTip = getCalibrationNozzleTip();
        if (calibrationNozzleTip != null && calibrationNozzleTip.getCalibration().isCalibrated()) {
            Location offset =
            		calibrationNozzleTip.getCalibration().getCalibratedOffset(location.getRotation());
            location = location.add(offset);
        }
        return location;
    }

    public boolean isChangerEnabled() {
        return changerEnabled;
    }

    public void setChangerEnabled(boolean changerEnabled) {
        this.changerEnabled = changerEnabled;
    }

    @Override
    public Wizard getConfigurationWizard() {
        return new ReferenceNozzleConfigurationWizard(this);
    }

    @Override
    public String getPropertySheetHolderTitle() {
        return getClass().getSimpleName() + " " + getName();
    }

    @Override
    public PropertySheetHolder[] getChildPropertySheetHolders() {
        ArrayList<PropertySheetHolder> children = new ArrayList<>();
        children.add(new NozzleTipsPropertySheetHolder(this, "Nozzle Tips", getNozzleTips(), null));
        return children.toArray(new PropertySheetHolder[] {});
    }

    @Override
    public PropertySheet[] getPropertySheets() {
        return new PropertySheet[] {
                new PropertySheetWizardAdapter(getConfigurationWizard()),
                new PropertySheetWizardAdapter(new ReferenceNozzleCameraOffsetWizard(this), "Offset Wizard")
        };
    }

    @Override
    public Action[] getPropertySheetHolderActions() {
        return new Action[] {deleteAction};
    }

    public Action deleteAction = new AbstractAction("Delete Nozzle") {
        {
            putValue(SMALL_ICON, Icons.nozzleRemove);
            putValue(NAME, "Delete Nozzle");
            putValue(SHORT_DESCRIPTION, "Delete the currently selected nozzle.");
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
           if (getHead().getNozzles().size() == 1) {
                MessageBoxes.errorBox(null, "Error: Nozzle Not Deleted", "Can't delete last nozzle. There must be at least one nozzle.");
                return;
            }
            int ret = JOptionPane.showConfirmDialog(MainFrame.get(),
                    "Are you sure you want to delete " + getName() + "?",
                    "Delete " + getName() + "?", JOptionPane.YES_NO_OPTION);
            if (ret == JOptionPane.YES_OPTION) {
                getHead().removeNozzle(ReferenceNozzle.this);
            }
        }
    };

    @Override
    public String toString() {
        return getName() + " " + getId();
    }

    public Length getSafeZ() {
        return safeZ;
    }

    public void setSafeZ(Length safeZ) {
        this.safeZ = safeZ;
    }

    @Override
    public void moveTo(Location location) throws Exception {
        moveTo(location, getHead().getMachine().getSpeed());
    }

    @Override
    public void moveToSafeZ() throws Exception {
        moveToSafeZ(getHead().getMachine().getSpeed());
    }

    ReferenceDriver getDriver() {
        return getMachine().getDriver();
    }

    ReferenceMachine getMachine() {
        return (ReferenceMachine) Configuration.get().getMachine();
    }
}
