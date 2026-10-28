/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.ws.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Two-factor state of a user, also used as the response of the TOTP setup ({@code secret}/{@code otpAuthUri} are
 * only set there).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonMfaStatus {

    private Boolean enabled;
    private Boolean required;
    private Integer recoveryCodesLeft;
    private String secret;
    private String otpAuthUri;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Integer getRecoveryCodesLeft() {
        return recoveryCodesLeft;
    }

    public void setRecoveryCodesLeft(Integer recoveryCodesLeft) {
        this.recoveryCodesLeft = recoveryCodesLeft;
    }

    /**
     * @return Base32 TOTP secret for manual entry in an authenticator app
     */
    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * @return {@code otpauth://} URI, shown as QR code
     */
    public String getOtpAuthUri() {
        return otpAuthUri;
    }

    public void setOtpAuthUri(String otpAuthUri) {
        this.otpAuthUri = otpAuthUri;
    }
}
