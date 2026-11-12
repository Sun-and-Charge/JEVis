// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

package convert

import (
	"fmt"
	"strings"

	"github.com/gopcua/opcua/id"
	"github.com/gopcua/opcua/ua"
)

// ParseNodeID parses the OPC UA string form of a NodeId (ns=2;s=x, i=84, ...).
func ParseNodeID(s string) (*ua.NodeID, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return nil, fmt.Errorf("empty NodeId")
	}
	// gopcua takes anything else as string id in namespace 0; the protocol
	// requires the explicit identifier type.
	idPart := s
	if strings.HasPrefix(idPart, "ns=") {
		if i := strings.Index(idPart, ";"); i > 0 {
			idPart = idPart[i+1:]
		}
	}
	if len(idPart) < 2 || idPart[1] != '=' || !strings.ContainsRune("isgb", rune(idPart[0])) {
		return nil, fmt.Errorf("invalid NodeId %q (expected ns=<n>;{i,s,g,b}=<id>)", s)
	}
	n, err := ua.ParseNodeID(s)
	if err != nil {
		return nil, fmt.Errorf("invalid NodeId %q: %v", s, err)
	}
	return n, nil
}

// NodeIDString formats a NodeId; nil gives "".
func NodeIDString(n *ua.NodeID) string {
	if n == nil {
		return ""
	}
	return n.String()
}

// ExpandedNodeIDString formats an ExpandedNodeId. A local one (no namespace
// URI, server index 0) is formatted as a plain NodeId.
func ExpandedNodeIDString(e *ua.ExpandedNodeID) string {
	if e == nil || e.NodeID == nil {
		return ""
	}
	s := e.NodeID.String()
	if e.NamespaceURI != "" {
		// replace the "ns=x;" part with the namespace URI
		if i := strings.Index(s, ";"); strings.HasPrefix(s, "ns=") && i > 0 {
			s = s[i+1:]
		}
		s = "nsu=" + e.NamespaceURI + ";" + s
	}
	if e.ServerIndex > 0 {
		s = fmt.Sprintf("svr=%d;%s", e.ServerIndex, s)
	}
	return s
}

// IsNullNodeID reports whether the NodeId is missing or the null NodeId i=0.
func IsNullNodeID(n *ua.NodeID) bool {
	return n == nil || (n.Namespace() == 0 && n.Type() <= ua.NodeIDTypeNumeric && n.IntID() == 0)
}

// QualifiedNameString formats a QualifiedName as "ns:name".
func QualifiedNameString(q *ua.QualifiedName) string {
	if q == nil {
		return ""
	}
	return fmt.Sprintf("%d:%s", q.NamespaceIndex, q.Name)
}

// LocalizedTextString returns the text of a LocalizedText.
func LocalizedTextString(l *ua.LocalizedText) string {
	if l == nil {
		return ""
	}
	return l.Text
}

var nodeClassNames = map[ua.NodeClass]string{
	ua.NodeClassObject:        "Object",
	ua.NodeClassVariable:      "Variable",
	ua.NodeClassMethod:        "Method",
	ua.NodeClassObjectType:    "ObjectType",
	ua.NodeClassVariableType:  "VariableType",
	ua.NodeClassReferenceType: "ReferenceType",
	ua.NodeClassDataType:      "DataType",
	ua.NodeClassView:          "View",
}

// NodeClassName returns the name of a node class ("Object", ...).
func NodeClassName(c ua.NodeClass) string {
	if n, ok := nodeClassNames[c]; ok {
		return n
	}
	return "Unspecified"
}

// NodeClassFromName parses a node class name (case-insensitive).
func NodeClassFromName(s string) (ua.NodeClass, bool) {
	for c, n := range nodeClassNames {
		if strings.EqualFold(n, s) {
			return c, true
		}
	}
	return 0, false
}

// DataTypeName returns the name of a standard data type (ns=0), e.g. "Double"
// or "UtcTime"; other data types are returned as NodeId string.
func DataTypeName(n *ua.NodeID) string {
	if n == nil {
		return ""
	}
	if n.Namespace() == 0 && n.Type() <= ua.NodeIDTypeNumeric {
		if name := id.Name(n.IntID()); name != "" {
			return name
		}
	}
	return n.String()
}
