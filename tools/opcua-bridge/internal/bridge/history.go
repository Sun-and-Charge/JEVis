// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Sun and Charge Germany GmbH

package bridge

import (
	"context"
	"strings"
	"time"

	"github.com/Sun-and-Charge/JEVis/tools/opcua-bridge/internal/convert"
	"github.com/Sun-and-Charge/JEVis/tools/opcua-bridge/internal/protocol"
	"github.com/gopcua/opcua/id"
	"github.com/gopcua/opcua/ua"
)

type historyCommon struct {
	protocol.Common
	NodeID        string `json:"nodeId"`
	From          string `json:"from"`
	To            string `json:"to"`
	MaxValues     int64  `json:"maxValues"`
	ValuesPerCall uint32 `json:"valuesPerCall"`
	Timestamps    string `json:"timestamps"`
}

type historyRawParams struct {
	historyCommon
	ReturnBounds *bool `json:"returnBounds"`
}

type historyProcessedParams struct {
	historyCommon
	Aggregate                     string   `json:"aggregate"`
	IntervalMs                    *float64 `json:"intervalMs"`
	UseServerCapabilitiesDefaults *bool    `json:"useServerCapabilitiesDefaults"`
	TreatUncertainAsBad           bool     `json:"treatUncertainAsBad"`
	PercentDataBad                uint8    `json:"percentDataBad"`
	PercentDataGood               uint8    `json:"percentDataGood"`
	UseSlopedExtrapolation        bool     `json:"useSlopedExtrapolation"`
}

type historyValue struct {
	TS         any    `json:"ts"`
	Value      any    `json:"value"`
	Status     string `json:"status"`
	StatusCode uint32 `json:"statusCode"`
}

type historyResult struct {
	Values    []historyValue `json:"values"`
	Truncated bool           `json:"truncated"`
}

// maxHistoryCalls limits the HistoryRead round trips of one request.
const maxHistoryCalls = 100000

type historyQuery struct {
	node       *ua.NodeID
	from, to   time.Time
	maxValues  int64
	timestamps ua.TimestampsToReturn
}

func parseHistoryCommon(cmd string, p historyCommon) (*historyQuery, *protocol.Error) {
	n, err := convert.ParseNodeID(p.NodeID)
	if err != nil {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "%s: %v", cmd, err)
	}
	q := &historyQuery{node: n, maxValues: p.MaxValues}
	if p.From == "" || p.To == "" {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "%s: from and to are required", cmd)
	}
	if q.from, err = convert.ParseTime(p.From); err != nil {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "%s: from: %v", cmd, err)
	}
	if q.to, err = convert.ParseTime(p.To); err != nil {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "%s: to: %v", cmd, err)
	}
	if p.MaxValues < 0 {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "%s: maxValues must not be negative", cmd)
	}
	switch strings.ToLower(str(&p.Timestamps, "Source")) {
	case "source":
		q.timestamps = ua.TimestampsToReturnSource
	case "server":
		q.timestamps = ua.TimestampsToReturnServer
	case "both":
		q.timestamps = ua.TimestampsToReturnBoth
	default:
		return nil, protocol.Errorf(protocol.CodeBadRequest, "%s: timestamps must be Source, Server or Both", cmd)
	}
	return q, nil
}

func (b *Bridge) historyRaw(req *protocol.Request) (any, *protocol.Error) {
	var p historyRawParams
	if err := req.Decode(&p); err != nil {
		return nil, err
	}
	q, perr := parseHistoryCommon(req.Cmd, p.historyCommon)
	if perr != nil {
		return nil, perr
	}
	details := &ua.ReadRawModifiedDetails{
		IsReadModified:   false,
		StartTime:        q.from,
		EndTime:          q.to,
		NumValuesPerNode: p.ValuesPerCall,
		ReturnBounds:     p.ReturnBounds == nil || *p.ReturnBounds,
	}
	eo := &ua.ExtensionObject{
		TypeID:       ua.NewFourByteExpandedNodeID(0, id.ReadRawModifiedDetails_Encoding_DefaultBinary),
		EncodingMask: ua.ExtensionObjectBinary,
		Value:        details,
	}
	return b.history(p.Common, q, eo)
}

func (b *Bridge) historyProcessed(req *protocol.Request) (any, *protocol.Error) {
	var p historyProcessedParams
	if err := req.Decode(&p); err != nil {
		return nil, err
	}
	if p.ValuesPerCall != 0 {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "%s: valuesPerCall is only supported by history-raw", req.Cmd)
	}
	q, perr := parseHistoryCommon(req.Cmd, p.historyCommon)
	if perr != nil {
		return nil, perr
	}
	agg, err := convert.ParseNodeID(p.Aggregate)
	if err != nil {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "%s: aggregate: %v", req.Cmd, err)
	}
	if p.IntervalMs == nil || *p.IntervalMs < 0 {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "%s: intervalMs is required", req.Cmd)
	}
	if p.PercentDataBad > 100 || p.PercentDataGood > 100 {
		return nil, protocol.Errorf(protocol.CodeBadRequest, "%s: percentDataBad/percentDataGood must be 0..100", req.Cmd)
	}
	details := &ua.ReadProcessedDetails{
		StartTime:          q.from,
		EndTime:            q.to,
		ProcessingInterval: *p.IntervalMs,
		AggregateType:      []*ua.NodeID{agg},
		AggregateConfiguration: &ua.AggregateConfiguration{
			UseServerCapabilitiesDefaults: p.UseServerCapabilitiesDefaults == nil || *p.UseServerCapabilitiesDefaults,
			TreatUncertainAsBad:           p.TreatUncertainAsBad,
			PercentDataBad:                p.PercentDataBad,
			PercentDataGood:               p.PercentDataGood,
			UseSlopedExtrapolation:        p.UseSlopedExtrapolation,
		},
	}
	eo := &ua.ExtensionObject{
		TypeID:       ua.NewFourByteExpandedNodeID(0, id.ReadProcessedDetails_Encoding_DefaultBinary),
		EncodingMask: ua.ExtensionObjectBinary,
		Value:        details,
	}
	return b.history(p.Common, q, eo)
}

// history runs HistoryRead with the given details and follows continuation
// points until done or maxValues is reached.
func (b *Bridge) history(common protocol.Common, q *historyQuery, details *ua.ExtensionObject) (any, *protocol.Error) {
	c, perr := b.session()
	if perr != nil {
		return nil, perr
	}
	ctx, cancel := b.context(common)
	defer cancel()

	out := historyResult{Values: []historyValue{}}
	var cp []byte
	for call := 0; ; call++ {
		if call >= maxHistoryCalls {
			b.releaseHistory(ctx, c, q, details, cp)
			return nil, protocol.Errorf(protocol.CodeOPCUA, "history: too many continuation points")
		}
		res, err := c.HistoryRead(ctx, &ua.HistoryReadRequest{
			HistoryReadDetails: details,
			TimestampsToReturn: q.timestamps,
			NodesToRead:        []*ua.HistoryReadValueID{{NodeID: q.node, ContinuationPoint: cp, DataEncoding: &ua.QualifiedName{}}},
		})
		if err != nil {
			return nil, uaError(err, protocol.CodeInternal)
		}
		if len(res.Results) != 1 || res.Results[0] == nil {
			return nil, protocol.Errorf(protocol.CodeOPCUA, "history: %d results for 1 node", len(res.Results))
		}
		r := res.Results[0]
		if convert.IsBad(r.StatusCode) {
			if r.StatusCode&0xFFFF0000 == ua.StatusBadNoData {
				break // no data in the range: empty result
			}
			return nil, statusError("history "+q.node.String(), r.StatusCode)
		}
		if r.HistoryData != nil && r.HistoryData.Value != nil {
			data, ok := r.HistoryData.Value.(*ua.HistoryData)
			if !ok {
				return nil, protocol.Errorf(protocol.CodeOPCUA, "history: unexpected history data %T", r.HistoryData.Value)
			}
			for _, dv := range data.DataValues {
				out.Values = append(out.Values, historyValueOf(dv, q.timestamps))
			}
		}
		cp = r.ContinuationPoint
		if q.maxValues > 0 && int64(len(out.Values)) >= q.maxValues {
			if int64(len(out.Values)) > q.maxValues || len(cp) > 0 {
				out.Truncated = true
			}
			out.Values = out.Values[:q.maxValues]
			if len(cp) > 0 {
				b.releaseHistory(ctx, c, q, details, cp)
			}
			break
		}
		if len(cp) == 0 {
			break
		}
	}
	return out, nil
}

// releaseHistory releases a continuation point that is not followed.
func (b *Bridge) releaseHistory(ctx context.Context, c Client, q *historyQuery, details *ua.ExtensionObject, cp []byte) {
	if len(cp) == 0 {
		return
	}
	_, err := c.HistoryRead(ctx, &ua.HistoryReadRequest{
		HistoryReadDetails:        details,
		TimestampsToReturn:        q.timestamps,
		ReleaseContinuationPoints: true,
		NodesToRead:               []*ua.HistoryReadValueID{{NodeID: q.node, ContinuationPoint: cp, DataEncoding: &ua.QualifiedName{}}},
	})
	if err != nil {
		b.Log.Printf("releasing continuation point: %v", err)
	}
}

func historyValueOf(dv *ua.DataValue, ts ua.TimestampsToReturn) historyValue {
	if dv == nil {
		return historyValue{Status: convert.StatusName(ua.StatusBadNoData), StatusCode: uint32(ua.StatusBadNoData)}
	}
	t := dv.SourceTimestamp
	if ts == ua.TimestampsToReturnServer || convert.FormatTime(t) == "" {
		t = dv.ServerTimestamp
	}
	return historyValue{
		TS:         convert.FormatTimePtr(t),
		Value:      convert.VariantValue(dv.Value),
		Status:     convert.StatusName(dv.Status),
		StatusCode: uint32(dv.Status),
	}
}
