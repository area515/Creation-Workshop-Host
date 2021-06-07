package org.area515.resinprinter.gcode;

import org.area515.resinprinter.printer.Printer;

public class PhotocentricGCodeControl extends eGENERICGCodeControl {
    public PhotocentricGCodeControl(Printer printer) {
        super(printer);
    }
    
    public String executeMoveZ(double dist) {
        return executeSingleCommand(String.format("G1 Z%1.3f F100.0\r\n", dist));
    }

    public String executeZHome() {
        return executeSingleCommand("G28\r\n");
    }
}
