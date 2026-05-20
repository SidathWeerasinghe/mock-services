// Package payload generates mock API responses of exact target sizes.
package payload

import (
	"bytes"
	"crypto/rand"
	"encoding/json"
	"encoding/xml"
	"fmt"
	"math"
	mathrand "math/rand"
	"strings"
	"time"
)

// ValidSizesKB lists allowed payload sizes in kilobytes.
var ValidSizesKB = []int{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20}

type Attributes struct {
	Color    string  `json:"color" xml:"color"`
	Size     string  `json:"size" xml:"size"`
	Weight   float64 `json:"weight" xml:"weight"`
	Priority int     `json:"priority" xml:"priority"`
	Region   string  `json:"region" xml:"region"`
}

type Item struct {
	ID          string     `json:"id" xml:"id"`
	Index       int        `json:"index" xml:"index"`
	Resource    string     `json:"resource" xml:"resource"`
	Name        string     `json:"name" xml:"name"`
	Description string     `json:"description" xml:"description"`
	Active      bool       `json:"active" xml:"active"`
	Score       int        `json:"score" xml:"score"`
	Tags        []string   `json:"tags" xml:"tags>tag"`
	CreatedAt   string     `json:"createdAt" xml:"createdAt"`
	Attributes  Attributes `json:"attributes" xml:"attributes"`
	Padding     string     `json:"padding" xml:"padding"`
}

type Metadata struct {
	Server       string `json:"server" xml:"server"`
	Timestamp    string `json:"timestamp" xml:"timestamp"`
	Method       string `json:"method" xml:"method"`
	Resource     string `json:"resource" xml:"resource"`
	TargetSizeKb int    `json:"targetSizeKb" xml:"targetSizeKb"`
	RequestID    string `json:"requestId" xml:"requestId"`
	Status       string `json:"status" xml:"status"`
	ContentType  string `json:"contentType" xml:"contentType"`
}

type Envelope struct {
	XMLName  xml.Name `xml:"MockResponse"`
	Metadata Metadata `json:"metadata" xml:"metadata"`
	Count    int      `json:"count" xml:"count"`
	Items    []Item   `json:"items" xml:"items>item"`
}

// ValidateSize returns an error if sizeKb is not allowed.
func ValidateSize(sizeKb int) error {
	for _, s := range ValidSizesKB {
		if s == sizeKb {
			return nil
		}
	}
	return fmt.Errorf("invalid size: %d KB. Valid values: %v", sizeKb, ValidSizesKB)
}

// Generate builds a payload of approximately sizeKb kilobytes.
func Generate(sizeKb int, format, method, resource string) (string, error) {
	if err := ValidateSize(sizeKb); err != nil {
		return "", err
	}
	targetBytes := sizeKb * 1024
	env := buildEnvelope(sizeKb, method, resource)

	switch strings.ToLower(format) {
	case "xml":
		return padXML(env, targetBytes)
	case "text":
		return padText(env, targetBytes), nil
	case "html":
		return padHTML(env, targetBytes, method, resource), nil
	default:
		return padJSON(env, targetBytes)
	}
}

func buildEnvelope(sizeKb int, method, resource string) *Envelope {
	now := time.Now().UTC().Format(time.RFC3339Nano)
	meta := Metadata{
		Server:       "MockAPIServer/1.0",
		Timestamp:    now,
		Method:       strings.ToUpper(method),
		Resource:     resource,
		TargetSizeKb: sizeKb,
		RequestID:    newUUID(),
		Status:       "200 OK",
		ContentType:  "application/json",
	}
	items := make([]Item, 5)
	for i := 1; i <= 5; i++ {
		items[i-1] = buildItem(i, resource, "")
	}
	return &Envelope{Metadata: meta, Count: len(items), Items: items}
}

func buildItem(index int, resource, padding string) Item {
	return Item{
		ID:          newUUID(),
		Index:       index,
		Resource:    resource,
		Name:        fmt.Sprintf("Mock %s #%d", resource, index),
		Description: fmt.Sprintf("Auto-generated mock record for %s", resource),
		Active:      true,
		Score:       int(math.Round(mathrand.Float64() * 100)),
		Tags:        []string{"mock", resource, "generated"},
		CreatedAt:   time.Now().UTC().Format(time.RFC3339Nano),
		Attributes:  buildAttributes(index),
		Padding:     padding,
	}
}

func buildAttributes(seed int) Attributes {
	return Attributes{
		Color:    pickFrom(seed, "red", "green", "blue", "yellow", "purple"),
		Size:     pickFrom(seed+1, "small", "medium", "large", "xlarge"),
		Weight:   float64(seed) * 13.7,
		Priority: seed%5 + 1,
		Region:   pickFrom(seed+2, "APAC", "EMEA", "AMER", "LATAM"),
	}
}

func pickFrom(seed int, values ...string) string {
	if len(values) == 0 {
		return ""
	}
	return values[abs(seed)%len(values)]
}

func abs(n int) int {
	if n < 0 {
		return -n
	}
	return n
}

func buildPadString(length int) string {
	if length <= 0 {
		return ""
	}
	unit := "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	var b strings.Builder
	for b.Len() < length {
		b.WriteString(unit)
	}
	return b.String()[:length]
}

func marshalJSON(env *Envelope) ([]byte, error) {
	return json.MarshalIndent(env, "", "  ")
}

func padJSON(env *Envelope, targetBytes int) (string, error) {
	base, err := marshalJSON(env)
	if err != nil {
		return "", err
	}
	if len(base) >= targetBytes {
		return string(base), nil
	}
	last := &env.Items[len(env.Items)-1]
	padNeeded := targetBytes - len(base)
	if padNeeded > 0 {
		last.Padding = buildPadString(padNeeded)
	}
	result, err := marshalJSON(env)
	if err != nil {
		return "", err
	}
	diff := targetBytes - len(result)
	if diff > 0 {
		last.Padding += buildPadString(diff)
		result, err = marshalJSON(env)
	} else if diff < 0 {
		trimTo := max(0, len(last.Padding)+diff)
		last.Padding = last.Padding[:trimTo]
		result, err = marshalJSON(env)
	}
	if err != nil {
		return "", err
	}
	return string(result), nil
}

func padXML(env *Envelope, targetBytes int) (string, error) {
	base, err := xml.MarshalIndent(env, "", "  ")
	if err != nil {
		return "", err
	}
	if len(base) >= targetBytes {
		return string(base), nil
	}
	last := &env.Items[len(env.Items)-1]
	padNeeded := targetBytes - len(base)
	last.Padding = buildPadString(padNeeded)
	result, err := xml.MarshalIndent(env, "", "  ")
	if err != nil {
		return "", err
	}
	diff := targetBytes - len(result)
	if diff > 0 {
		last.Padding += buildPadString(diff)
		result, err = xml.MarshalIndent(env, "", "  ")
	} else if diff < 0 {
		trimTo := max(0, len(last.Padding)+diff)
		last.Padding = last.Padding[:trimTo]
		result, err = xml.MarshalIndent(env, "", "  ")
	}
	if err != nil {
		return "", err
	}
	return string(result), nil
}

func padText(env *Envelope, targetBytes int) string {
	meta := env.Metadata
	var sb strings.Builder
	sb.WriteString("=== Mock API Server Response ===\n")
	sb.WriteString(fmt.Sprintf("server      : %s\n", meta.Server))
	sb.WriteString(fmt.Sprintf("timestamp   : %s\n", meta.Timestamp))
	sb.WriteString(fmt.Sprintf("method      : %s\n", meta.Method))
	sb.WriteString(fmt.Sprintf("resource    : %s\n", meta.Resource))
	sb.WriteString(fmt.Sprintf("targetSizeKb: %d\n", meta.TargetSizeKb))
	sb.WriteString(fmt.Sprintf("requestId   : %s\n", meta.RequestID))
	sb.WriteString(fmt.Sprintf("status      : %s\n", meta.Status))
	sb.WriteString(fmt.Sprintf("count       : %d\n", env.Count))
	sb.WriteString("---\n")
	for _, item := range env.Items {
		sb.WriteString("[item]\n")
		writeItemFields(&sb, item)
	}
	sb.WriteString("[padding]\n")
	base := sb.String()
	padNeeded := targetBytes - len([]byte(base))
	padNeeded = max(0, padNeeded-1)
	return base + buildPadString(padNeeded) + "\n"
}

func writeItemFields(sb *strings.Builder, item Item) {
	fmt.Fprintf(sb, "  id = %s\n", item.ID)
	fmt.Fprintf(sb, "  index = %d\n", item.Index)
	fmt.Fprintf(sb, "  resource = %s\n", item.Resource)
	fmt.Fprintf(sb, "  name = %s\n", item.Name)
	fmt.Fprintf(sb, "  description = %s\n", item.Description)
	fmt.Fprintf(sb, "  active = %t\n", item.Active)
	fmt.Fprintf(sb, "  score = %d\n", item.Score)
	fmt.Fprintf(sb, "  tags = %v\n", item.Tags)
	fmt.Fprintf(sb, "  createdAt = %s\n", item.CreatedAt)
	fmt.Fprintf(sb, "  attributes = {%s %s %.1f %d %s}\n",
		item.Attributes.Color, item.Attributes.Size, item.Attributes.Weight,
		item.Attributes.Priority, item.Attributes.Region)
}

func padHTML(env *Envelope, targetBytes int, method, resource string) string {
	meta := env.Metadata
	var rows bytes.Buffer
	for _, item := range env.Items {
		fmt.Fprintf(&rows, "<tr><td>%s</td><td>%d</td><td>%s</td><td>%s</td><td>%t</td><td>%d</td><td>%s</td></tr>\n",
			item.ID, item.Index, item.Name, item.Description, item.Active, item.Score, item.CreatedAt)
	}
	template := fmt.Sprintf(`<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"><title>Mock API Response — %s</title>
<style>body{font-family:sans-serif;background:#0d1117;color:#e6edf3;margin:24px}h1{color:#58a6ff;font-size:1.4rem;margin-bottom:8px}.meta{font-size:.85rem;color:#8b949e;margin-bottom:16px}table{border-collapse:collapse;width:100%%}th{background:#21262d;color:#58a6ff;padding:8px 12px;text-align:left;border:1px solid #30363d}td{padding:7px 12px;border:1px solid #30363d;font-size:.85rem}tr:nth-child(even){background:#161b22}</style></head>
<body>
<h1>Mock API Response</h1>
<div class="meta"><strong>%s</strong> /%s &nbsp;|&nbsp; requestId: %s &nbsp;|&nbsp; %s</div>
<table>
<thead><tr><th>ID</th><th>#</th><th>Name</th><th>Description</th><th>Active</th><th>Score</th><th>Created At</th></tr></thead>
<tbody>
%s</tbody>
</table>
<!-- [padding] `, resource, strings.ToUpper(method), resource, meta.RequestID, meta.Timestamp, rows.String())
	tail := " -->\n</body>\n</html>"
	padNeeded := max(0, targetBytes-len([]byte(template+tail)))
	return template + buildPadString(padNeeded) + tail
}

func max(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func newUUID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		uint32(b[0])<<24|uint32(b[1])<<16|uint32(b[2])<<8|uint32(b[3]),
		uint16(b[4])<<8|uint16(b[5]),
		uint16(b[6])<<8|uint16(b[7]),
		uint16(b[8])<<8|uint16(b[9]),
		uint64(b[10])<<40|uint64(b[11])<<32|uint64(b[12])<<24|uint64(b[13])<<16|uint64(b[14])<<8|uint64(b[15]))
}
