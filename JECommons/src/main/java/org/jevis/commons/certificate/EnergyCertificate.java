/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.certificate;

import java.util.ArrayList;
import java.util.List;

/**
 * An existing energy certificate (Energieausweis) of a building (tender point BA-07), stored as JSON in the "Data"
 * attribute of an object of class {@value #CLASS_NAME} below the building's {@value #DIRECTORY_CLASS_NAME}. The
 * certificate's PDF is the attribute {@value #ATTRIBUTE_FILE}. Creating certificates and requesting registration
 * numbers from the DIBt are not part of JEVis; the data are entered from the issued certificate.
 * <p>
 * Dates are ISO dates ({@code yyyy-MM-dd}); energy values in kWh/(m²·a), area in m², CO₂ in kg/(m²·a).
 */
public class EnergyCertificate {

    public static final String CLASS_NAME = "Energy Certificate";
    public static final String DIRECTORY_CLASS_NAME = "Energy Certificate Directory";
    public static final String ATTRIBUTE_DATA = "Data";
    public static final String ATTRIBUTE_FILE = "File";
    public static final String ATTRIBUTE_TRANSFER_STATE = "Transfer State";

    /** Verbrauchsausweis (measured consumption) or Bedarfsausweis (calculated demand) */
    public enum Type {CONSUMPTION, DEMAND}

    /** use of the building, as on the certificate */
    public enum Use {RESIDENTIAL, NON_RESIDENTIAL}

    private Type type;
    private Use use;
    private String registrationNumber;
    private String issued;
    private String validUntil;
    private String issuer;
    /** users and groups responsible for the certificate (reminders before it expires) */
    private List<Long> responsible;
    private String energyClass;
    private Double finalEnergy;
    private Double primaryEnergy;
    private Double heatConsumption;
    private Double electricityConsumption;
    private Double referenceArea;
    private Double co2;
    /** months before {@link #validUntil} with a reminder mail, default 6 and 1 */
    private List<Integer> reminderMonths;
    private String note;
    private String created;
    private String createdBy;

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Use getUse() {
        return use;
    }

    public void setUse(Use use) {
        this.use = use;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getIssued() {
        return issued;
    }

    public void setIssued(String issued) {
        this.issued = issued;
    }

    public String getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(String validUntil) {
        this.validUntil = validUntil;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public List<Long> getResponsible() {
        if (responsible == null) responsible = new ArrayList<>();
        return responsible;
    }

    public void setResponsible(List<Long> responsible) {
        this.responsible = responsible;
    }

    public String getEnergyClass() {
        return energyClass;
    }

    public void setEnergyClass(String energyClass) {
        this.energyClass = energyClass;
    }

    public Double getFinalEnergy() {
        return finalEnergy;
    }

    public void setFinalEnergy(Double finalEnergy) {
        this.finalEnergy = finalEnergy;
    }

    public Double getPrimaryEnergy() {
        return primaryEnergy;
    }

    public void setPrimaryEnergy(Double primaryEnergy) {
        this.primaryEnergy = primaryEnergy;
    }

    public Double getHeatConsumption() {
        return heatConsumption;
    }

    public void setHeatConsumption(Double heatConsumption) {
        this.heatConsumption = heatConsumption;
    }

    public Double getElectricityConsumption() {
        return electricityConsumption;
    }

    public void setElectricityConsumption(Double electricityConsumption) {
        this.electricityConsumption = electricityConsumption;
    }

    public Double getReferenceArea() {
        return referenceArea;
    }

    public void setReferenceArea(Double referenceArea) {
        this.referenceArea = referenceArea;
    }

    public Double getCo2() {
        return co2;
    }

    public void setCo2(Double co2) {
        this.co2 = co2;
    }

    public List<Integer> getReminderMonths() {
        if (reminderMonths == null || reminderMonths.isEmpty()) {
            List<Integer> defaults = new ArrayList<>();
            defaults.add(6);
            defaults.add(1);
            return defaults;
        }
        return reminderMonths;
    }

    public void setReminderMonths(List<Integer> reminderMonths) {
        this.reminderMonths = reminderMonths;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
