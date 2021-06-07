package org.area515.resinprinter.gcode;

import java.io.IOException;

import org.area515.resinprinter.printer.Printer;

/// <summary>
/// This class is the go-between for sending gcode commands to the Device interface
/// This is here so delegate functions can be bound not to a GUI control for control of the printer
/// </summary>
public class SedgwickGCodeControl extends eGENERICGCodeControl {
	public SedgwickGCodeControl(Printer printer) {
		super(printer);
	}

    public String executeMoveX(double dist) {
    	return executeSingleCommand("O\r\n");
    }

    public String executeMoveY(double dist) {
    	return executeSingleCommand("C\r\n");
    }

    public String executeMoveZ(double dist) {
        if (dist > .024 && dist < .026) { // small reverse 
            return executeSingleCommand("Y\r\n");
        }
        if (dist == 10.0) { // large reverse
            return executeSingleCommand("I\r\n");
        }
        if (dist < -.024 && dist > -.026) { // small forward
            return executeSingleCommand("H\r\n");
        }
        if (dist == -1.0) {  // medium forward
            return executeSingleCommand("J\r\n");
        }
        if (dist == -10.0) {  // large forward
            return executeSingleCommand("K\r\n");
        }
        //if (dist == 1.0) { // medium reverse
            return executeSingleCommand("U\r\n");
        //}
    }

    public String executeSetRelativePositioning() {
    	return "\r\n";
    }
    
    //Sedgwick doesn't have a welcome mat
    @Override
    public String readWelcomeChitChatFromFirmwareSerialPort() throws IOException {
    	return null;
    }
    
    public String executeMotorsOn() {
    	return executeSingleCommand("E\r\n");
    }

    public String executeMotorsOff(){
        return executeSingleCommand("D\r\n");
    }
}