/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of OPCUA-Driver.
 * <p>
 * OPCUA-Driver is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * OPCUA-Driver is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * OPCUA-Driver. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * OPCUA-Driver is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeopc;

import org.joda.time.DateTime;

import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

/**
 * Command line client for tests: lists the endpoints, connects, browses and reads the history of a node.
 * <p>
 * Arguments: url [user password]
 */
public class CMDClient {

    public CMDClient() {

    }

    public static void main(String[] args) throws OpcException {
        System.out.println("Start CommandLine OPC UA Client using URL: " + args[0]);
        OPCClient opcClient = new OPCClient(args[0]);

        System.out.println("List Endpoints:");
        String username = "";
        String password = "";
        if (args.length > 2) {
            username = args[1];
            password = args[2];
        }
        int i = 0;
        int auto = -1;
        List<OpcEndpoint> endpoints = opcClient.getEndpoints();
        for (OpcEndpoint endpointDescription : endpoints) {
            System.out.printf("%nEP[%s]:  %s %s/%s level %s user tokens %s%n", i, endpointDescription.getUrl(),
                    endpointDescription.getSecurityPolicy(), endpointDescription.getSecurityMode(),
                    endpointDescription.getSecurityLevel(), endpointDescription.getUserTokens());
            if (endpointDescription.isSecurityNone()) {
                System.out.println("... Found Unsecured Endpoint");
                auto = i;
            }
            OPCClient.printEP(endpointDescription);
            i++;
        }

        System.out.println("\nAutoselect EP: " + auto);

        Scanner scanner = new Scanner(System.in);
        OpcEndpoint endpointDescription = auto >= 0 ? endpoints.get(auto) : null;
        try {
            while (true) {
                System.out.println("\n\nSelect endpoint ID, leave emtpy if same");
                String line = scanner.nextLine();

                if (!line.isEmpty()) {
                    endpointDescription = endpoints.get(Integer.parseInt(line));
                }

                opcClient.setEndpoints(endpointDescription);
                if (!username.isEmpty() && !password.isEmpty()) {
                    System.out.println("Using username: " + username);
                    opcClient.setIdentification(username, password);
                }
                opcClient.connect();
                System.out.println("\nConnection successful");

                System.out.println("\nBrowse Device?(y/n)");
                String browse = scanner.nextLine();
                if (browse.equalsIgnoreCase("y")) {
                    getDP(opcClient);
                }

                System.out.println("\nfetch samples?(for example: ns=1;s=SM_E_38_WA )");
                String fetch = scanner.nextLine();
                if (!fetch.isEmpty()) {
                    printData(opcClient, fetch);
                }
            }
        } catch (Exception ex) {
            // System.in has been closed
            System.out.println("System.in was closed; exiting");
            ex.printStackTrace();
        } finally {
            opcClient.close();
        }
    }


    private static void printData(OPCClient opcClient, String id) throws OpcException {
        System.out.println("Fetch Datapoint: '" + id + "'");

        DateTime from = new DateTime(DateTime.now().getYear(), 1, 1, 15, 0, 0);
        DateTime until = DateTime.now();

        OPCUAChannel OPCUAChannel = new OPCUAChannel(id, "i=11505", 900000);

        List<OpcValue> valueList = opcClient.getHistory(OPCUAChannel, from, until);
        for (OpcValue dataValue : valueList) {
            System.out.println("sample: " + dataValue);
        }
    }

    private static void getDP(OPCClient opcClient) {
        System.out.println("Fetch Nodes: please wait....");
        HashMap<String, OpcReference> map = opcClient.browse();
        map.forEach((s, referenceDescription) -> {
            System.out.println("New Des: " + referenceDescription.getName() + " " + referenceDescription.getNodeId());
            System.out.println("-" + referenceDescription);
        });
        System.out.println("Done");
    }


}
