/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.iso.add;

import org.jevis.iso.add.FormAttribute.FormAttributeType;

import static org.jevis.iso.add.FormAttribute.FormAttributeType.*;
import static org.jevis.iso.add.FormAttribute.FormAttributeType.Boolean;
import static org.jevis.iso.add.FormAttribute.FormAttributeType.Double;
import static org.jevis.iso.add.FormAttribute.FormAttributeType.Long;

/**
 * @author <gerrit.schutz@sunandcharge.com>Gerrit Schutz</gerrit.schutz@sunandcharge.com>
 */
public class FormAttributeDisplayType {

    private FormAttributeType output = null;

    public FormAttributeDisplayType(int primitiveType, String displayType) {

        switch (primitiveType) {
            case 0:
                //String
                if (displayType != null) {
                    switch (displayType) {
                        case ("Text"):
                            //Simple String
                            output = Text;
                            break;
                        case ("Text Area"):
                            //Text Field
                            output = TextArea;
                            break;
                        case ("Password"):
                            //Text Password
                            output = TextPassword;
                            break;
                        case ("Date"):
                            //Date
                            output = Date;
                            break;
                        case ("Date Time"):
                            output = DateTime;
                            break;
                        case ("Schedule"):
                            output = Schedule;
                            break;
                        case ("Time Zone"):
                            output = TimeZone;
                            break;
                        case ("Object Target"):
                            output = ObjectTarget;
                            break;
                        case ("Attribute Target"):
                            output = AttributeTarget;
                            break;
                        case ("String Enum"):
                            output = StringEnum;
                            break;
                        default:
                            break;
                    }
                } else {
                    output = Text;
                }
                break;
            case 1:
                //Double
                //make choices for different type
                output = Double;
                break;
            case 2:
                //Long
                output = Long;
                break;
            case 3:
                //File
                output = File;
                break;
            case 4:
                //Boolean
                output = Boolean;
                break;
            case 5:
                //Selection
                output = Selection;
                break;
            case 6:
                //Multi Selection
                output = MultiSelection;
                break;
            case 7:
                //Password PBKDF2
                output = Password;
                break;
            default:
                break;
        }
    }

    public FormAttributeType getOutput() {

        return output;
    }
}
