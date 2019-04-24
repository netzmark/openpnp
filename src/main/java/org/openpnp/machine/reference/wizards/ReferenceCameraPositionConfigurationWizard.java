package org.openpnp.machine.reference.wizards;

import java.awt.Color;

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
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.gui.support.LengthConverter;
import org.openpnp.gui.support.MutableLocationProxy;
import org.openpnp.machine.reference.ReferenceCamera;
import org.openpnp.model.Configuration;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

@SuppressWarnings("serial")
public class ReferenceCameraPositionConfigurationWizard extends AbstractConfigurationWizard {
    private final ReferenceCamera referenceCamera;

    private JTextField textFieldOffX;
    private JTextField textFieldOffY;
    private JTextField textFieldOffZ;
    private JPanel panelOffsets;
    private JPanel panelLocation;
    private JLabel lblX;
    private JLabel lblX1; ///+
    private JLabel lblX2; ///+
    private JLabel lblX3; ///+
    private JLabel lblY;
    private JLabel lblY1; ///+
    private JLabel lblY2; ///+
    private JLabel lblY3; ///+
    private JLabel lblZ;
    private JLabel lblRotation_1;
    private JTextField textFieldLocationX;
    private JTextField textFieldLocationX1; ///+
    private JTextField textFieldLocationX2; ///+
    private JTextField textFieldLocationX3; ///+
    private JTextField textFieldLocationY;
    private JTextField textFieldLocationY1; ///+
    private JTextField textFieldLocationY2; ///+
    private JTextField textFieldLocationY3; ///+
    private JTextField textFieldLocationZ;
    private JTextField textFieldLocationRotation;
    private LocationButtonsPanel locationButtonsPanel;
    private JTextField textFieldSafeZ;


    public ReferenceCameraPositionConfigurationWizard(ReferenceCamera referenceCamera) {
        this.referenceCamera = referenceCamera;

        panelOffsets = new JPanel();
        contentPanel.add(panelOffsets);
        panelOffsets.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null),
                "Offsets", TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
        panelOffsets.setLayout(new FormLayout(new ColumnSpec[] {
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,},
            new RowSpec[] {
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC, ///+
                FormSpecs.DEFAULT_ROWSPEC, ///+
                FormSpecs.RELATED_GAP_ROWSPEC, ///+
                FormSpecs.DEFAULT_ROWSPEC, ///+
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,}));

        JLabel olblX = new JLabel("X");
        panelOffsets.add(olblX, "2, 2");

        JLabel olblY = new JLabel("Y");
        panelOffsets.add(olblY, "4, 2");

        JLabel olblZ = new JLabel("Z");
        panelOffsets.add(olblZ, "6, 2");


        textFieldOffX = new JTextField();
        panelOffsets.add(textFieldOffX, "2, 4");
        textFieldOffX.setColumns(8);

        textFieldOffY = new JTextField();
        panelOffsets.add(textFieldOffY, "4, 4");
        textFieldOffY.setColumns(8);

        textFieldOffZ = new JTextField();
        panelOffsets.add(textFieldOffZ, "6, 4");
        textFieldOffZ.setColumns(8);

        JPanel panelSafeZ = new JPanel();
        panelSafeZ.setBorder(new TitledBorder(null, "Safe Z", TitledBorder.LEADING,
                TitledBorder.TOP, null, null));
        contentPanel.add(panelSafeZ);
        panelSafeZ.setLayout(new FormLayout(
                new ColumnSpec[] {FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,
                        FormSpecs.RELATED_GAP_COLSPEC, FormSpecs.DEFAULT_COLSPEC,},
                new RowSpec[] {FormSpecs.RELATED_GAP_ROWSPEC, FormSpecs.DEFAULT_ROWSPEC,}));

        JLabel lblSafeZ = new JLabel("Safe Z");
        panelSafeZ.add(lblSafeZ, "2, 2, right, default");

        textFieldSafeZ = new JTextField();
        panelSafeZ.add(textFieldSafeZ, "4, 2, fill, default");
        textFieldSafeZ.setColumns(10);

        panelLocation = new JPanel();
        panelLocation.setBorder(new TitledBorder(null, "Location", TitledBorder.LEADING,
                TitledBorder.TOP, null, null));
        contentPanel.add(panelLocation);
        panelLocation.setLayout(new FormLayout(new ColumnSpec[] {
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
                FormSpecs.RELATED_GAP_ROWSPEC,  ///+
                FormSpecs.DEFAULT_ROWSPEC,  ///+
                FormSpecs.RELATED_GAP_ROWSPEC,  ///+
                FormSpecs.DEFAULT_ROWSPEC,  ///+
                FormSpecs.RELATED_GAP_ROWSPEC,  ///+
                FormSpecs.DEFAULT_ROWSPEC,  ///+
                FormSpecs.RELATED_GAP_ROWSPEC,  ///+
                FormSpecs.DEFAULT_ROWSPEC,  ///+
                FormSpecs.RELATED_GAP_ROWSPEC,  ///+
                FormSpecs.DEFAULT_ROWSPEC,  ///+
                FormSpecs.RELATED_GAP_ROWSPEC,  ///+
                FormSpecs.DEFAULT_ROWSPEC,  ///+
                FormSpecs.RELATED_GAP_ROWSPEC,  ///+
                FormSpecs.DEFAULT_ROWSPEC,  ///+
                FormSpecs.RELATED_GAP_ROWSPEC,  ///+
                FormSpecs.DEFAULT_ROWSPEC,  ///+
                FormSpecs.RELATED_GAP_ROWSPEC,  ///+
                FormSpecs.DEFAULT_ROWSPEC,  ///+
                FormSpecs.RELATED_GAP_ROWSPEC,  ///+
                FormSpecs.DEFAULT_ROWSPEC,  ///+
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,}));

        lblX = new JLabel("X");
        panelLocation.add(lblX, "2, 2");
        
        lblX1 = new JLabel("N1 X-offset");  ///+
        panelLocation.add(lblX1, "2, 10");
        
        lblX2 = new JLabel("N2 X-offset");  ///+
        panelLocation.add(lblX2, "2, 16");
        
        lblX3 = new JLabel("N3 X-offset");  ///+
        panelLocation.add(lblX3, "2, 22");

        lblY = new JLabel("Y");
        panelLocation.add(lblY, "4, 2");
        
        lblY1 = new JLabel("N1 Y-offset");  ///+
        panelLocation.add(lblY1, "4, 10");
        
        lblY2 = new JLabel("N2 Y-offset");  ///+
        panelLocation.add(lblY2, "4, 16");
        
        lblY3 = new JLabel("N3 Y-offset");  ///+
        panelLocation.add(lblY3, "4, 22");

        lblZ = new JLabel("Z");
        panelLocation.add(lblZ, "6, 2");

        lblRotation_1 = new JLabel("Rotation");
        panelLocation.add(lblRotation_1, "8, 2");

        textFieldLocationX = new JTextField();
        panelLocation.add(textFieldLocationX, "2, 4, fill, default");
        textFieldLocationX.setColumns(8);
        
        textFieldLocationX1 = new JTextField(); ///+
        panelLocation.add(textFieldLocationX1, "2, 12, fill, default");
        textFieldLocationX1.setColumns(8);
        
        textFieldLocationX2 = new JTextField(); ///+
        panelLocation.add(textFieldLocationX2, "2, 18, fill, default");
        textFieldLocationX2.setColumns(8);
        
        textFieldLocationX3 = new JTextField(); ///+
        panelLocation.add(textFieldLocationX3, "2, 24, fill, default");
        textFieldLocationX3.setColumns(8);

        textFieldLocationY = new JTextField();
        panelLocation.add(textFieldLocationY, "4, 4, fill, default");
        textFieldLocationY.setColumns(8);
        
        textFieldLocationY1 = new JTextField();
        panelLocation.add(textFieldLocationY1, "4, 12, fill, default");
        textFieldLocationY1.setColumns(8);
        
        textFieldLocationY2 = new JTextField();
        panelLocation.add(textFieldLocationY2, "4, 18, fill, default");
        textFieldLocationY2.setColumns(8);
        
        textFieldLocationY3 = new JTextField();
        panelLocation.add(textFieldLocationY3, "4, 24, fill, default");
        textFieldLocationY3.setColumns(8);

        textFieldLocationZ = new JTextField();
        panelLocation.add(textFieldLocationZ, "6, 4, fill, default");
        textFieldLocationZ.setColumns(8);

        textFieldLocationRotation = new JTextField();
        panelLocation.add(textFieldLocationRotation, "8, 4, fill, default");
        textFieldLocationRotation.setColumns(8);

        try {
            // Causes WindowBuilder to fail, so just throw away the error.
            if (referenceCamera.getHead() == null) {
                // Fixed camera, add the location fields and buttons and turn off offsets.
                locationButtonsPanel = new LocationButtonsPanel(textFieldLocationX,
                        textFieldLocationY, textFieldLocationZ, textFieldLocationRotation);
                panelLocation.add(locationButtonsPanel, "10, 4, fill, fill");
                panelOffsets.setVisible(false);    
                panelSafeZ.setVisible(false);
            }
            else {
                // Moving camera, hide location and show only offsets.
                panelLocation.setVisible(false);
            }
        }
        catch (Exception e) {

        }
    }

    @Override
    public void createBindings() {
        DoubleConverter doubleConverter =
                new DoubleConverter(Configuration.get().getLengthDisplayFormat());
        LengthConverter lengthConverter = new LengthConverter();

        if (referenceCamera.getHead() == null) {
            // fixed camera
            MutableLocationProxy headOffsets = new MutableLocationProxy();
            bind(UpdateStrategy.READ_WRITE, referenceCamera, "headOffsets", headOffsets,
                    "location");
            addWrappedBinding(headOffsets, "lengthX", textFieldLocationX, "text", lengthConverter);
            addWrappedBinding(referenceCamera, "xofs1", textFieldLocationX1, "text", doubleConverter); ///+
            addWrappedBinding(referenceCamera, "xofs2", textFieldLocationX2, "text", doubleConverter); ///+
            addWrappedBinding(referenceCamera, "xofs3", textFieldLocationX3, "text", doubleConverter); ///+
            addWrappedBinding(headOffsets, "lengthY", textFieldLocationY, "text", lengthConverter);
            addWrappedBinding(referenceCamera, "yofs1", textFieldLocationY1, "text", doubleConverter); ///+
            addWrappedBinding(referenceCamera, "yofs2", textFieldLocationY2, "text", doubleConverter); ///+
            addWrappedBinding(referenceCamera, "yofs3", textFieldLocationY3, "text", doubleConverter); ///+
            addWrappedBinding(headOffsets, "lengthZ", textFieldLocationZ, "text", lengthConverter);
            addWrappedBinding(headOffsets, "rotation", textFieldLocationRotation, "text",
                    doubleConverter);
        }
        else {
            // moving camera
            MutableLocationProxy headOffsets = new MutableLocationProxy();
            bind(UpdateStrategy.READ_WRITE, referenceCamera, "headOffsets", headOffsets,
                    "location");
            addWrappedBinding(headOffsets, "lengthX", textFieldOffX, "text", lengthConverter);
            addWrappedBinding(headOffsets, "lengthY", textFieldOffY, "text", lengthConverter);
            addWrappedBinding(headOffsets, "lengthZ", textFieldOffZ, "text", lengthConverter);
            addWrappedBinding(referenceCamera, "safeZ", textFieldSafeZ, "text", lengthConverter);
        }

        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldOffX);
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldOffY);
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldOffZ);

        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldLocationX);
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldLocationX1); ///+
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldLocationX2); ///+
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldLocationX3); ///+
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldLocationY);
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldLocationY1); ///+
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldLocationY2); ///+
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldLocationY3); ///+
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldLocationZ);
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldLocationRotation);
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldSafeZ);
    }
}
