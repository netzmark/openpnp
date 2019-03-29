package org.openpnp.spi.base;

import javax.swing.Icon;

import org.openpnp.ConfigurationListener;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Configuration;
import org.openpnp.model.Part;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.Nozzle;
import org.simpleframework.xml.Attribute;

public abstract class AbstractFeeder extends AbstractModelObject implements Feeder {
    @Attribute
    protected String id;

    @Attribute(required = false)
    protected String name;

    @Attribute
    protected boolean enabled;

    @Attribute
    protected String partId;
    
    @Attribute(required=false)
    protected int retryCount = 3;
    
    @Attribute(required=false)    
    protected int alignRetryCount = 0;
    
    @Attribute(required=false)    
    protected int pickRetryCount = 0;
    
    @Attribute(required=false)    
    protected boolean autoSkipA = false;
    
    @Attribute(required=false)    
    protected boolean autoSkipP = false;
    
    protected Part part;

    public AbstractFeeder() {
        this.id = Configuration.createId("FDR");
        this.name = getClass().getSimpleName();
        Configuration.get().addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationLoaded(Configuration configuration) throws Exception {
                part = configuration.getPart(partId);
            }
        });
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        Object oldValue = this.enabled;
        this.enabled = enabled;
        firePropertyChange("enabled", oldValue, enabled);
    }

    @Override
    public void setPart(Part part) {
        this.part = part;
        this.partId = part.getId();
    }

    @Override
    public Part getPart() {
        return part;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Icon getPropertySheetHolderIcon() {
        return Icons.feeder;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getAlignRetryCount() {
        return alignRetryCount;
    }
    
    public int getPickRetryCount() {
        return pickRetryCount;
    }
    
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
    
    public void setAlignRetryCount(int alignRetryCount) {
        this.alignRetryCount = alignRetryCount;
    }
    
    public void setPickRetryCount(int pickRetryCount) {
        this.pickRetryCount = pickRetryCount;
    }
    
    public boolean getAutoSkipA() {
        return autoSkipA;
    }
    
    public void setAutoSkipA(boolean autoSkipA) {
        this.autoSkipA = autoSkipA;
    }
    
    public boolean getAutoSkipP() {
        return autoSkipP;
    }
    
    public void setAutoSkipP(boolean autoSkipP) {
        this.autoSkipP = autoSkipP;
    }
    
    @Override
    public PropertySheet[] getPropertySheets() {
        return new PropertySheet[] {new PropertySheetWizardAdapter(getConfigurationWizard(), "Configuration")};
    }
    
    public void postPick(Nozzle nozzle) throws Exception { }
}