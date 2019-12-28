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

package org.openpnp.machine.reference.wizards;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

import org.openpnp.Translations;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.machine.reference.ReferencePnpJobProcessor;
import org.openpnp.machine.reference.ReferencePnpJobProcessor.JobOrderHint;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

@SuppressWarnings("serial")
public class ReferencePnpJobProcessorConfigurationWizard extends AbstractConfigurationWizard {
    private final ReferencePnpJobProcessor jobProcessor;
    private JCheckBox parkWhenComplete;
    private JComboBox comboBoxJobOrder;
    private JCheckBox checkBoxAutoSaveJobAfterPlacement;
    private JCheckBox checkBoxAutoSaveConfiguration;
    private JCheckBox checkBoxDisableAutomatics;
    private JCheckBox checkBoxAutoSkipDisabledFeeders;
    private JCheckBox checkBoxAutoDisableFeeder;
    private JCheckBox checkBoxDisableTipChanging;
    private JTextField sizeThresholdTf; //sizeThreshold

    public ReferencePnpJobProcessorConfigurationWizard(ReferencePnpJobProcessor jobProcessor) {
        this.jobProcessor = jobProcessor;
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        JPanel panelGeneral = new JPanel();
        panelGeneral.setBorder(new TitledBorder(null, "General", TitledBorder.LEADING,
                TitledBorder.TOP, null, null));
        contentPanel.add(panelGeneral);
        panelGeneral.setLayout(new FormLayout(
                new ColumnSpec[] {
                        FormSpecs.RELATED_GAP_COLSPEC,
                        FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC,
                        FormSpecs.DEFAULT_COLSPEC,
                        },
                new RowSpec[] {
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        }
                
                ));

        JLabel lblParkWhenComplete = new JLabel(Translations.getString("MachineSetup.JobProcessors.ReferencePnpJobProcessor.Label.ParkWhenComplete"));
        panelGeneral.add(lblParkWhenComplete, "2, 2, right, default");

        parkWhenComplete = new JCheckBox("");
        panelGeneral.add(parkWhenComplete, "4, 2");

        JLabel lblJobOrder = new JLabel(Translations.getString("MachineSetup.JobProcessors.ReferencePnpJobProcessor.Label.JobOrder"));
        panelGeneral.add(lblJobOrder, "2, 4, right, default");

        comboBoxJobOrder = new JComboBox(JobOrderHint.values());
        panelGeneral.add(comboBoxJobOrder, "4, 4");
        
        JLabel lblAutoSaveJobAfterPlacement = new JLabel(Translations.getString("MachineSetup.JobProcessors.ReferencePnpJobProcessor.Label.AutoSaveJobAfterPlacement"));
        panelGeneral.add(lblAutoSaveJobAfterPlacement, "2, 6, right, default");

        checkBoxAutoSaveJobAfterPlacement = new JCheckBox("");
        panelGeneral.add(checkBoxAutoSaveJobAfterPlacement, "4, 6");
        
        JLabel lblAutoSaveConfiguration = new JLabel(Translations.getString("MachineSetup.JobProcessors.ReferencePnpJobProcessor.Label.AutoSaveConfiguration") + " " + (jobProcessor.getConfigSaveFrequencyMs() / 1000 / 60) + " min");
        panelGeneral.add(lblAutoSaveConfiguration, "2, 8, right, default");//
        lblAutoSaveConfiguration.setToolTipText(Translations.getString(Translations.getString("MachineSetup.JobProcessors.ReferencePnpJobProcessor.Label.DelayInfo")));
        
        checkBoxAutoSaveConfiguration = new JCheckBox("");
        panelGeneral.add(checkBoxAutoSaveConfiguration, "4, 8");
       
        JLabel lblDisableAutomatics = new JLabel("Disable Feeder's Automatic Skipping and Disabling");
        panelGeneral.add(lblDisableAutomatics, "2, 16, right, default");
        lblDisableAutomatics.setToolTipText("Global <one touch> disabling any feeder's automation.");
        
        checkBoxDisableAutomatics = new JCheckBox("");
        panelGeneral.add(checkBoxDisableAutomatics, "4, 16");
        
        JLabel lblAutoDisableFeeder = new JLabel("Automatically Disable Feeder at its Auto Skip");
        panelGeneral.add(lblAutoDisableFeeder, "2, 18, right, default");
        lblAutoDisableFeeder.setToolTipText("When Auto Skip is performed the Feeder is disabled.");
        
        checkBoxAutoDisableFeeder = new JCheckBox("");
        panelGeneral.add(checkBoxAutoDisableFeeder, "4, 18");
        
        JLabel lblAutoSkipDisabledFeeders = new JLabel("Automatically Skip Disabled Feeders");
        panelGeneral.add(lblAutoSkipDisabledFeeders, "2, 20, right, default");
        lblAutoSkipDisabledFeeders.setToolTipText("Skip the placement if Feeder is disabled and Auto Skip allowed.");
        
        checkBoxAutoSkipDisabledFeeders = new JCheckBox("");
        panelGeneral.add(checkBoxAutoSkipDisabledFeeders, "4, 20");
        
        JLabel lblDisableTipChanging = new JLabel("Disable Tips Changing");
        panelGeneral.add(lblDisableTipChanging, "2, 22, right, default");
        lblDisableTipChanging.setToolTipText("Don't load unloaded tips during the Job.");
        
        checkBoxDisableTipChanging = new JCheckBox("");
        panelGeneral.add(checkBoxDisableTipChanging, "4, 22");
        
        JLabel lblSizeThreshold = new JLabel("after there is less parts to assemble than:");
        panelGeneral.add(lblSizeThreshold, "4, 24, left, default");
                
        sizeThresholdTf = new JTextField();
        sizeThresholdTf.setText("100000");
        panelGeneral.add(sizeThresholdTf, "4, 26, fill, default");
        sizeThresholdTf.setColumns(16);
        sizeThresholdTf.setToolTipText("0 means the same like <Changings enabled>. Set the value higehr than number of parts in the Job to have this active always."); 
        
    }
    
    @Override
    public void createBindings() {
        IntegerConverter intConverter = new IntegerConverter();
        
        addWrappedBinding(jobProcessor, "parkWhenComplete", parkWhenComplete, "selected");
        addWrappedBinding(jobProcessor, "jobOrder", comboBoxJobOrder, "selectedItem");
        addWrappedBinding(jobProcessor, "autoSaveJob", checkBoxAutoSaveJobAfterPlacement, "selected");
        addWrappedBinding(jobProcessor, "autoSaveConfiguration", checkBoxAutoSaveConfiguration, "selected");
        addWrappedBinding(jobProcessor, "disableAutomatics", checkBoxDisableAutomatics, "selected");
        addWrappedBinding(jobProcessor, "autoSkipDisabledFeeders", checkBoxAutoSkipDisabledFeeders, "selected");
        addWrappedBinding(jobProcessor, "autoDisableFeeder", checkBoxAutoDisableFeeder, "selected");
        addWrappedBinding(jobProcessor, "disableTipChanging", checkBoxDisableTipChanging, "selected");
        addWrappedBinding(jobProcessor, "sizeThreshold", sizeThresholdTf, "text", intConverter);
    }
}
