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

package org.openpnp.machine.reference.feeder.wizards;

import java.awt.Color;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.openpnp.gui.components.ComponentDecorators;
import org.openpnp.gui.components.LocationButtonsPanel;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.DoubleConverter;
import org.openpnp.gui.support.IdentifiableListCellRenderer;
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.gui.support.LengthConverter;
import org.openpnp.gui.support.MutableLocationProxy;
import org.openpnp.gui.support.PartsComboBoxModel;
import org.openpnp.machine.reference.ReferenceFeeder;
import org.openpnp.model.Configuration;
import org.openpnp.model.Part;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

@SuppressWarnings("serial")
/**
 * TODO: This should become it's own property sheet which the feeders can include.
 */
public abstract class AbstractReferenceFeederConfigurationWizard
        extends AbstractConfigurationWizard {
    private final ReferenceFeeder feeder;
    private final boolean includePickLocation;

    private JPanel panelLocation;
    private JLabel lblX_1;
    private JLabel lblY_1;
    private JLabel lblZ;
    private JLabel lblRotation;
    private JTextField textFieldLocationX;
    private JTextField textFieldLocationY;
    private JTextField textFieldLocationZ;
    private JTextField textFieldLocationC;
    private JPanel panelPart;

    private JComboBox comboBoxPart;
    private LocationButtonsPanel locationButtonsPanel;
    private JTextField retryCountTf;
    private JTextField alignRetryCountTf;
    private JTextField pickRetryCountTf;
    private JCheckBox chckbxAutoSkipAlign;
    private JCheckBox chckbxAutoSkipPick;

    /**
     * @wbp.parser.constructor
     */
    public AbstractReferenceFeederConfigurationWizard(ReferenceFeeder feeder) {
        this(feeder, true);
    }

    public AbstractReferenceFeederConfigurationWizard(ReferenceFeeder feeder,
            boolean includePickLocation) {
        this.feeder = feeder;
        this.includePickLocation = includePickLocation;

        panelPart = new JPanel();
        panelPart.setBorder(
                new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null), "General Settings", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
        contentPanel.add(panelPart);
        panelPart.setLayout(new FormLayout(new ColumnSpec[] {
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("default:grow"),},
            new RowSpec[] {
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,}));

        comboBoxPart = new JComboBox();
        try {
            comboBoxPart.setModel(new PartsComboBoxModel());
        }
        catch (Throwable t) {
            // Swallow this error. This happens during parsing in
            // in WindowBuilder but doesn't happen during normal run.
        }
        
        JLabel lblPart = new JLabel("Part");
        panelPart.add(lblPart, "2, 2, right, default");
        comboBoxPart.setRenderer(new IdentifiableListCellRenderer<Part>());
        panelPart.add(comboBoxPart, "4, 2, left, default");
        
        JLabel lblRetryCount = new JLabel("Feed Retry Count");
        panelPart.add(lblRetryCount, "2, 4, right, default");
        
        retryCountTf = new JTextField();
        retryCountTf.setText("3");
        panelPart.add(retryCountTf, "4, 4");
        retryCountTf.setColumns(3);
        
        JLabel lblAlignRetryCount = new JLabel("Align Retry Count");
        panelPart.add(lblAlignRetryCount, "6, 2, right, default");
                
        alignRetryCountTf = new JTextField();
        alignRetryCountTf.setText("2");
        panelPart.add(alignRetryCountTf, "8, 2, fill, default");
        alignRetryCountTf.setColumns(16);
                
        JLabel lblPickRetryCount = new JLabel("Pick Retry Count");
        panelPart.add(lblPickRetryCount, "6, 4, right, default");
                
        pickRetryCountTf = new JTextField();
        pickRetryCountTf.setText("3");
        panelPart.add(pickRetryCountTf, "8, 4, fill, default");
        pickRetryCountTf.setColumns(16);
        
        chckbxAutoSkipAlign = new JCheckBox("");
        panelPart.add(chckbxAutoSkipAlign, "10, 2, right, default");
        
        JLabel lblAutoSkipAlign = new JLabel("AutoSkip on Align Error");
        panelPart.add(lblAutoSkipAlign, "12, 2, left, default"); 
        
        chckbxAutoSkipPick = new JCheckBox("");
        panelPart.add(chckbxAutoSkipPick, "10, 4, right, default");
        
        JLabel lblAutoSkipPick = new JLabel("AutoSkip on Feed/Pick Error");
        panelPart.add(lblAutoSkipPick, "12, 4, left, default"); 
        
        lblRetryCount.setToolTipText("The number of Feeds to retry. <0> means just single Feeding with no retries.");
        retryCountTf.setToolTipText("The number of Feeds to retry. <0> means just single Feeding with no retries.");      
        lblAlignRetryCount.setToolTipText("The number of Aligns (with Picks) to retry. <0> means just single Alignment with no retries.");
        alignRetryCountTf.setToolTipText("The number of Aligns (with Picks) to retry. <0> means just single Alignment with no retries.");      
        lblPickRetryCount.setToolTipText("The number of Picks to retry. <0> means just single Picking with no retries.");
        pickRetryCountTf.setToolTipText("The number of Picks to retry. <0> is deafult and means single Picking with no retries.");      
        
        chckbxAutoSkipAlign.setToolTipText("Skip placement when number of retries is reached.");
        lblAutoSkipAlign.setToolTipText("Skip placement when number of retries is reached.");
        chckbxAutoSkipPick.setToolTipText("Skip placement when number of retries is reached.");
        lblAutoSkipPick.setToolTipText("Skip placement when number of retries is reached.");
        
        if (includePickLocation) {
            panelLocation = new JPanel();
            panelLocation.setBorder(new TitledBorder(
                    new EtchedBorder(EtchedBorder.LOWERED, null, null), "Pick Location",
                    TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
            contentPanel.add(panelLocation);
            panelLocation
                    .setLayout(new FormLayout(
                            new ColumnSpec[] {FormSpecs.RELATED_GAP_COLSPEC,
                                    ColumnSpec.decode("default:grow"),
                                    FormSpecs.RELATED_GAP_COLSPEC, ColumnSpec
                                            .decode("default:grow"),
                                    FormSpecs.RELATED_GAP_COLSPEC,
                                    ColumnSpec.decode("default:grow"),
                                    FormSpecs.RELATED_GAP_COLSPEC,
                                    ColumnSpec.decode("default:grow"),
                                    FormSpecs.RELATED_GAP_COLSPEC,
                                    ColumnSpec.decode("left:default:grow"),},
                            new RowSpec[] {FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,
                                    FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,}));

            lblX_1 = new JLabel("X");
            panelLocation.add(lblX_1, "2, 2");

            lblY_1 = new JLabel("Y");
            panelLocation.add(lblY_1, "4, 2");

            lblZ = new JLabel("Z");
            panelLocation.add(lblZ, "6, 2");

            lblRotation = new JLabel("Rotation");
            panelLocation.add(lblRotation, "8, 2");

            textFieldLocationX = new JTextField();
            panelLocation.add(textFieldLocationX, "2, 4");
            textFieldLocationX.setColumns(8);

            textFieldLocationY = new JTextField();
            panelLocation.add(textFieldLocationY, "4, 4");
            textFieldLocationY.setColumns(8);

            textFieldLocationZ = new JTextField();
            panelLocation.add(textFieldLocationZ, "6, 4");
            textFieldLocationZ.setColumns(8);

            textFieldLocationC = new JTextField();
            panelLocation.add(textFieldLocationC, "8, 4");
            textFieldLocationC.setColumns(8);

            locationButtonsPanel = new LocationButtonsPanel(textFieldLocationX, textFieldLocationY,
                    textFieldLocationZ, textFieldLocationC);
            panelLocation.add(locationButtonsPanel, "10, 4");
        }
    }

    @Override
    public void createBindings() {
        LengthConverter lengthConverter = new LengthConverter();
        IntegerConverter intConverter = new IntegerConverter();
        DoubleConverter doubleConverter =
                new DoubleConverter(Configuration.get().getLengthDisplayFormat());

        addWrappedBinding(feeder, "part", comboBoxPart, "selectedItem");
        addWrappedBinding(feeder, "retryCount", retryCountTf, "text", intConverter);
        addWrappedBinding(feeder, "alignRetryCount", alignRetryCountTf, "text", intConverter);
        addWrappedBinding(feeder, "pickRetryCount", pickRetryCountTf, "text", intConverter);
        addWrappedBinding(feeder, "autoSkipAlign", chckbxAutoSkipAlign, "selected");
        addWrappedBinding(feeder, "autoSkipPick", chckbxAutoSkipPick, "selected");        
        
        if (includePickLocation) {
            MutableLocationProxy location = new MutableLocationProxy();
            bind(UpdateStrategy.READ_WRITE, feeder, "location", location, "location");
            addWrappedBinding(location, "lengthX", textFieldLocationX, "text", lengthConverter);
            addWrappedBinding(location, "lengthY", textFieldLocationY, "text", lengthConverter);
            addWrappedBinding(location, "lengthZ", textFieldLocationZ, "text", lengthConverter);
            addWrappedBinding(location, "rotation", textFieldLocationC, "text", doubleConverter);
            ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldLocationX);
            ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldLocationY);
            ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldLocationZ);
            ComponentDecorators.decorateWithAutoSelect(textFieldLocationC);
        }

        ComponentDecorators.decorateWithAutoSelect(retryCountTf);
        ComponentDecorators.decorateWithAutoSelect(alignRetryCountTf);
        ComponentDecorators.decorateWithAutoSelect(pickRetryCountTf);
        
    }
}