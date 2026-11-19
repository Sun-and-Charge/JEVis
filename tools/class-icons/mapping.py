# Copyright (C) 2026 Sun and Charge Germany GmbH
# This file is part of JEVis, licensed under the GNU General Public License version 3.
"""Class icon mapping (J25.5): every PNG in var/classes -> Material Symbol, category colour, folder or not.

Writes mapping.tsv: <relative png path> TAB <kind tile|folder> TAB <glyph> TAB <hex colour>
"""
import os
import sys

ROOT = sys.argv[1]
OUT = sys.argv[2]

C = {
    'org': '#2f4a78',      # organisation, sites, general folders
    'raw': '#2563eb',      # raw data
    'clean': '#0891b2',    # clean data
    'data': '#4f46e5',     # other data kinds
    'source': '#475569',   # data sources, servers, channels, parsers
    'calc': '#7c3aed',     # calculations
    'view': '#0284c7',     # analyses, dashboards
    'report': '#be185d',   # reports
    'alarm': '#dc2626',    # alarms, notifications
    'finance': '#d97706',  # contracts, invoices, accounting
    'iso': '#16a34a',      # action plans, nonconformities, legal, maintenance, appointments, notes
    'asset': '#0d9488',    # meters, equipment
    'system': '#64748b',   # users, groups, services, plugins
}

# exact class name -> (glyph, category)
M = {
    # system directories and general structure
    'Organization': ('corporate_fare', 'org'), 'Organization Directory': ('corporate_fare', 'org'),
    'Building': ('location_on', 'org'), 'Directory': ('folder_open', 'org'),
    'Administration Directory': ('admin_panel_settings', 'org'), 'Monitored Object': ('visibility', 'org'),
    'Monitored Object Directory': ('visibility', 'org'), 'Equipment Register': ('inventory_2', 'asset'),
    'Document': ('description', 'org'), 'Document Directory': ('description', 'org'),
    'File': ('draft', 'org'), 'File Directory': ('draft', 'org'),
    'Custom Period': ('date_range', 'org'), 'Calendar Directory': ('calendar_month', 'org'),
    'View Directory': ('space_dashboard', 'view'), 'Measurement Directory': ('speed', 'asset'),
    'Data Directory': ('database', 'raw'), 'Data Source Directory': ('dns', 'source'),
    'Calculation Directory': ('functions', 'calc'), 'Analyses Directory': ('query_stats', 'view'),
    'Dashboard Directory': ('space_dashboard', 'view'), 'Alarm Directory': ('notifications', 'alarm'),
    'Base Data Directory': ('dataset', 'data'), 'NonconformityPlan Directory': ('report', 'iso'),
    # data
    'Data': ('database', 'raw'), 'Clean Data': ('filter_alt', 'clean'), 'Math Data': ('function', 'data'),
    'Forecast Data': ('trending_up', 'data'), 'String Data': ('text_fields', 'data'), 'User Data': ('edit_note', 'data'),
    'Link': ('link', 'data'), 'Base Data': ('dataset', 'data'), 'Data Notes': ('sticky_note_2', 'iso'),
    # calculations
    'Calculation': ('functions', 'calc'), 'Input': ('login', 'calc'), 'Output': ('logout', 'calc'),
    'Result Calculation Template': ('request_quote', 'calc'), 'Template Calculation Directory': ('request_quote', 'calc'),
    'Template Result Calculation Plugin': ('request_quote', 'system'),
    # views
    'Analysis': ('query_stats', 'view'), 'Dashboard Analysis': ('space_dashboard', 'view'),
    'Dashboard Collection': ('slideshow', 'view'),
    # alarms and notifications
    'Alarm Configuration': ('notifications', 'alarm'), 'Alarm': ('notification_important', 'alarm'),
    'Static Limit Alarm': ('notification_important', 'alarm'), 'Dynamic Limit Alarm': ('notification_important', 'alarm'),
    'Alarm Link': ('link', 'alarm'), 'Notification': ('campaign', 'alarm'), 'E-Mail Notification': ('mail', 'alarm'),
    'Export Data Event': ('schedule_send', 'alarm'), 'Export Directory': ('schedule_send', 'alarm'),
    # reports
    'Report': ('summarize', 'report'), 'Periodic Report': ('event_repeat', 'report'), 'Report Directory': ('summarize', 'report'),
    'Report Configuration': ('tune', 'report'), 'Report Attribute': ('label', 'report'), 'Report Link': ('link', 'report'),
    'Report Link Directory': ('link', 'report'), 'Report Period Configuration': ('date_range', 'report'),
    'Automated Working Sheet': ('table_chart', 'report'), 'Energy Management Link': ('link', 'report'),
    # finance
    'Accounting Configuration': ('calculate', 'finance'), 'Accounting Configuration Directory': ('calculate', 'finance'),
    'Energy Invoice': ('receipt_long', 'finance'), 'Energy Invoice Data': ('receipt', 'finance'),
    'Energy Invoices Directory': ('receipt_long', 'finance'), 'Governmental Dues': ('account_balance', 'finance'),
    'Energy Governmental Dues Directory': ('account_balance', 'finance'), 'Energy Supplier': ('storefront', 'finance'),
    'Energy Contractor': ('handshake', 'finance'), 'Energy Contractor Directory': ('handshake', 'finance'),
    'Energy Contracting Directory': ('handshake', 'finance'), 'Energy Supply Directory': ('handshake', 'finance'),
    'Energy Grid Operation Directory': ('electrical_services', 'finance'),
    'Energy Metering Point Operation Directory': ('electric_meter', 'finance'),
    # ISO
    'Action Plan v2': ('checklist', 'iso'), 'Action Plan Directory v2': ('checklist', 'iso'), 'Action': ('task_alt', 'iso'),
    'Action Directory': ('task_alt', 'iso'), 'NonconformityPlan': ('report', 'iso'), 'Nonconformity': ('error', 'iso'),
    'Nonconformities Directory': ('report', 'iso'), 'Index of Legal Provisions': ('gavel', 'iso'),
    'Index of Legal Provisions Directory': ('gavel', 'iso'), 'Obligation': ('policy', 'iso'), 'Obligation Directory': ('policy', 'iso'),
    'Maintenance Message': ('build', 'iso'), 'Maintenance Directory': ('build', 'iso'),
    'Appointment': ('event', 'iso'), 'Appointment Directory': ('event', 'iso'),
    # meters
    'Measurement Instrument': ('speed', 'asset'), 'Electricity Measurement Instrument': ('bolt', 'asset'),
    'Gas Measurement Instrument': ('local_fire_department', 'asset'), 'Water Measurement Instrument': ('water_drop', 'asset'),
    'Heat Measurement Instrument': ('heat', 'asset'), 'Temperature Measurement Instrument': ('device_thermostat', 'asset'),
    'Air Measurement Instrument': ('air', 'asset'), 'Compressed-Air Measurement Instrument': ('compress', 'asset'),
    'Nitrogen Measurement Instrument': ('science', 'asset'), 'Flow Measurement Instrument': ('waves', 'asset'),
    'Fluid Level Measurement Instrument': ('water_full', 'asset'), 'Pressure Measurement Instrument': ('readiness_score', 'asset'),
    'Reading Round': ('route', 'asset'), 'Reading Round Directory': ('route', 'asset'),
    # equipment
    'Building Equipment': ('domain', 'asset'), 'Air Conditioning': ('ac_unit', 'asset'), 'Compressor': ('compress', 'asset'),
    'Cooler': ('severe_cold', 'asset'), 'Engine': ('settings', 'asset'), 'Facade': ('window', 'asset'), 'Floor': ('layers', 'asset'),
    'HVAC System Simplified': ('hvac', 'asset'), 'Heat Generation': ('local_fire_department', 'asset'),
    'Lighting': ('lightbulb', 'asset'), 'Occupancy': ('groups', 'asset'), 'Office Equipment': ('computer', 'asset'),
    'Pantry Equipment': ('coffee', 'asset'), 'Plug Load': ('power', 'asset'), 'Production Equipment': ('precision_manufacturing', 'asset'),
    'Pump': ('valve', 'asset'), 'Recooler or Condenser': ('mode_fan', 'asset'), 'Register': ('list_alt', 'asset'),
    'Ventilation': ('air', 'asset'), 'Equipment Directory': ('handyman', 'asset'),
    'Air Condition Equipment Directory': ('ac_unit', 'asset'), 'Compressor Equipment Directory': ('compress', 'asset'),
    'Cooling Equipment Directory': ('severe_cold', 'asset'), 'Heating Equipment Directory': ('local_fire_department', 'asset'),
    'Lighting Equipment Directory': ('lightbulb', 'asset'), 'Office Equipment Directory': ('computer', 'asset'),
    'Pantry Equipment Directory': ('coffee', 'asset'), 'Production Equipment Directory': ('precision_manufacturing', 'asset'),
    'Ventilation Equipment Directory': ('air', 'asset'),
    # system
    'System': ('hub', 'system'), 'User': ('person', 'system'), 'User Directory': ('person', 'system'),
    'Group': ('group', 'system'), 'Group Directory': ('group', 'system'), 'User Role': ('badge', 'system'),
    'User Role Directory': ('badge', 'system'), 'Service': ('settings_suggest', 'system'), 'Service Directory': ('settings_suggest', 'system'),
    'Recycle Bin': ('delete', 'system'), 'Constants': ('pin', 'system'), 'Enum': ('format_list_bulleted', 'system'),
    'Enum Directory': ('format_list_bulleted', 'system'),
    # data collector
    'Data Server': ('dns', 'source'), 'Data Source': ('input', 'source'), 'Channel': ('cable', 'source'),
    'Channel Directory': ('cable', 'source'), 'Data Point': ('data_object', 'source'), 'Data Point Directory': ('data_object', 'source'),
    'Modbus Master': ('memory', 'source'), 'ProfiNET': ('memory', 'source'), 'VPN Connection': ('vpn_lock', 'source'),
    'Parser': ('code', 'source'), 'Converter': ('transform', 'source'), 'ZIP Converter': ('folder_zip', 'source'),
    'CSV Export': ('upload_file', 'source'), 'CSV Export Link': ('link', 'source'), 'Dynamic Channel Path': ('alt_route', 'source'),
    'EMail Plugin': ('mail', 'source'), 'SQL Request': ('manage_search', 'source'), 'SQL Request Directory': ('manage_search', 'source'),
    'VIDA350': ('memory', 'source'), 'Revolution PI Server': ('memory', 'source'), 'Loytec XML-DL Output Channel': ('output', 'source'),
    'OPC DA Server': ('dns', 'source'),
}

# plugins: the module symbol in system grey
PLUGINS = {
    'Accounting Plugin': 'calculate', 'Action Plan Plugin': 'checklist', 'Alarm Plugin': 'notifications',
    'Appointment Plugin': 'event', 'Base Data Plugin': 'dataset', 'Class Plugin': 'category', 'Configuration Plugin': 'account_tree',
    'Control Center Plugin': 'extension', 'Control Center': 'desktop_windows', 'Dashboard Plugin': 'space_dashboard',
    'Equipment Plugin': 'handyman', 'Graph Plugin': 'query_stats', 'Index of Legal Provisions Plugin': 'gavel',
    'Load Profile Plugin': 'ssid_chart', 'Loytec Plugin': 'memory', 'Maintenance Plugin': 'build', 'Map Plugin': 'map',
    'Meter Plugin': 'speed', 'Nonconformities Plugin': 'report', 'Notes Plugin': 'sticky_note_2', 'Unit Plugin': 'straighten',
    'Report Plugin': 'summarize', 'JEAlarm': 'notifications', 'JECalc': 'functions', 'JEDataCollector': 'download',
    'JEDataProcessor': 'filter_alt', 'JEDemo': 'play_circle', 'JENotifier': 'campaign', 'JEOPCUAWriter': 'upload',
    'JEReport': 'summarize', 'JEStatus': 'monitor_heart',
}


def rule(name):
    """generic rules for the protocol-specific data collector and accounting classes"""
    n = name.lower()
    if 'metering point' in n:
        return 'electric_meter', 'finance'
    if 'grid operat' in n:
        return 'electrical_services', 'finance'
    if 'supply contractor' in n:
        return 'handshake', 'finance'
    if 'measurement directory' in n:
        base = name.replace('Measurement Directory', 'Measurement Instrument')
        return M.get(base, ('speed', 'asset'))
    if n.endswith('server'):
        return 'dns', 'source'
    if 'channel' in n:
        return 'cable', 'source'
    if 'data point' in n:
        return 'data_object', 'source'
    if n.endswith('parser'):
        return 'code', 'source'
    return None


rows = []
missing = []
for dirpath, dirs, files in os.walk(ROOT):
    for f in sorted(files):
        if not f.lower().endswith('.png') or f == 'MissingPlaceholder.png':
            continue
        name = f[:-4]
        rel = os.path.relpath(os.path.join(dirpath, f), ROOT).replace('\\', '/')
        if name in PLUGINS:
            glyph, cat = PLUGINS[name], 'system'
        elif name in M:
            glyph, cat = M[name]
        else:
            r = rule(name)
            if r is None:
                missing.append(rel)
                continue
            glyph, cat = r
        kind = 'folder' if (name.endswith('Directory') or name.endswith('Directory v2')) else 'tile'
        if name == 'Directory':
            kind = 'tile'
        rows.append((rel, kind, glyph, C[cat]))

with open(OUT, 'w', encoding='utf-8') as h:
    for r in rows:
        h.write('\t'.join(r) + '\n')
print(len(rows), 'mapped,', len(missing), 'missing')
for m in missing:
    print('  missing', m)
print('glyphs:', ' '.join(sorted({r[2] for r in rows} | {'folder_fill1'})))
