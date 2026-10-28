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

import com.fasterxml.jackson.databind.JsonNode;
import org.jevis.commons.json.JsonTools;
import org.jevis.iso.rest.Login;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <gerrit.schutz@sunandcharge.com>Gerrit Schutz</gerrit.schutz@sunandcharge.com>
 */
public class Translations {

    public List<FormAttribute> translate(List<FormAttribute> listfa, String language) throws Exception {
        for (FormAttribute fa : listfa) {
            InputStream is = getResourceAsStream("/lang/" + language + ".json");
//            String jsonTxt = IOUtils.toString(is, "UTF-8");

//            JsonParser parser = new JsonParser();
//            JsonObject json = parser.parse(jsonTxt).getAsJsonObject();
            JsonNode jsonNode = JsonTools.objectMapper().readTree(is);
            JsonNode jsonNode1 = jsonNode.get(fa.getName());
            fa.setTransname(jsonNode1.textValue());

//            String att = fa.getName();

//            if (Objects.nonNull(json.get(fa.getName()))) {
//                att = json.get(fa.getName()).getAsString();
//            }

//            fa.setTransname(att);
        }
        return listfa;
    }

    public String getTranslatedKey(String language, String keyName) throws IOException {
        String output = "";
        InputStream is = getResourceAsStream("/lang/" + language + ".json");

        JsonNode jsonNode = JsonTools.objectMapper().readTree(is);
        JsonNode jsonNode1 = jsonNode.get(keyName);
        return jsonNode1.textValue();

//        String jsonTxt = IOUtils.toString(is, "UTF-8");
//
//        JsonParser parser = new JsonParser();
//        JsonObject json = parser.parse(jsonTxt).getAsJsonObject();
//        if (Objects.nonNull(json.get(keyName))) {
//            output = json.get(keyName).getAsString();
//        }
//        return output;
    }

    private List<String> getResourceFiles(String path) throws IOException {
        List<String> filenames = new ArrayList<>();

        try (
                InputStream in = getResourceAsStream(path);
                BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String resource;

            while ((resource = br.readLine()) != null) {
                filenames.add(resource);
            }
        }

        return filenames;
    }

    private InputStream getResourceAsStream(String resource) {
//        final InputStream in = getContextClassLoader().getResourceAsStream(resource);
//
//        return in == null ? getClass().getResourceAsStream(resource) : in;
        return Login.class.getResourceAsStream(resource);
    }

    private ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }
}
