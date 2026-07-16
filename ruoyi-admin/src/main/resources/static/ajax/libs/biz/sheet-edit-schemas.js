/**
 * 检测记录表网页编辑 schema（与 JsonSheetWordRenderer 字段及 Word 模板对齐）
 */
(function (global) {
    var STANDARD_HEADER_BASE = [
        { key: "inspectionUnitName", label: "检测单位名称", col: 6 },
        { key: "recordNumber", label: "记录编号", col: 6 },
        { key: "projectName", label: "工程名称", col: 6 },
        { key: "partUse", label: "工程部位/用途", col: 6 },
        { key: "sampleInfo", label: "样品信息", col: 6 },
        { key: "testDate", label: "试验检测日期", col: 6 },
        { key: "testCondition", label: "试验条件", col: 6 },
        { key: "workCondition", label: "测试工况", col: 6 },
        { key: "inspectionBasis", label: "检测依据", col: 6, templateFixed: true },
        { key: "judgementBasis", label: "判定依据", col: 6, templateFixed: true },
        { key: "equipment", label: "主要仪器设备名称及编号", col: 6 }
    ];

    function buildHeader(excludeKeys, extraFields, overrides) {
        var fields = [];
        STANDARD_HEADER_BASE.forEach(function (field) {
            if (excludeKeys && excludeKeys.indexOf(field.key) >= 0) {
                return;
            }
            var copy = {};
            Object.keys(field).forEach(function (k) { copy[k] = field[k]; });
            if (overrides && overrides[field.key]) {
                Object.keys(overrides[field.key]).forEach(function (k) {
                    copy[k] = overrides[field.key][k];
                });
            }
            fields.push(copy);
        });
        if (extraFields && extraFields.length) {
            fields = fields.concat(extraFields);
        }
        return fields;
    }

    function corrosionValueColumns() {
        var cols = [];
        for (var i = 1; i <= 20; i++) {
            cols.push({
                key: "value" + i,
                label: String(i),
                altKeys: ["potentialValue" + i]
            });
        }
        return cols;
    }

    global.WEB_EDIT_SCHEMAS = {
        carbon_depth: {
            recordsTitle: "测点数据",
            maxRecordsPerPage: 12,
            headerFields: buildHeader(["workCondition"]),
            recordColumns: [
                { key: "componentName", label: "构件名称" },
                { key: "point", label: "测点" },
                { key: "value1", label: "测值1" },
                { key: "value2", label: "测值2" },
                { key: "value3", label: "测值3" },
                { key: "average", label: "均值" }
            ]
        },
        rebar_corrosion: {
            recordsTitle: "测点数据",
            layout: "corrosion",
            maxRecordsPerPage: 7,
            headerFields: buildHeader(["workCondition"]),
            recordFixedColumns: [
                { key: "componentName", label: "构件名称" },
                { key: "point", label: "测区编号", altKeys: ["areaNumber"] },
                { key: "temperature", label: "温度（℃）" }
            ],
            recordValueColumns: corrosionValueColumns()
        },
        rebar_cover: {
            recordsTitle: "测区数据",
            layout: "nested",
            maxRecordsPerPage: 2,
            maxNestedItemsPerRecord: 10,
            headerFields: buildHeader(["workCondition"]),
            recordFixedColumns: [
                { key: "rowIndex", label: "序号" },
                { key: "componentName", label: "构件名称" },
                { key: "point", label: "测试位置", altKeys: ["testPosition", "serialNumber"] },
                { key: "direction", label: "钢筋方向", altKeys: ["rebarDirection"] }
            ],
            nestedKey: "rebarCoverItems",
            nestedAltKey: "rebars",
            nestedColumns: [
                { key: "rebarNo", label: "钢筋编号", altKeys: ["rebarNumber"] },
                { key: "value1", label: "保护层厚度1（mm）", altKeys: ["coverThickness1"] },
                { key: "value2", label: "保护层厚度2（mm）", altKeys: ["coverThickness2"] },
                { key: "average", label: "均值（mm）", altKeys: ["coverThicknessAverage"] },
                { key: "spacing1", label: "钢筋间距1（mm）", altKeys: ["rebarSpacing1"] },
                { key: "spacing2", label: "钢筋间距2（mm）", altKeys: ["rebarSpacing2"] },
                { key: "spacingAverage", label: "间距均值（mm）", altKeys: ["rebarSpacingAverage"] }
            ]
        },
        displacement_total_station: {
            recordsTitle: "测点坐标",
            layout: "coordinate",
            maxRecordsPerPage: 15,
            headerFields: buildHeader([]),
            coordinateSections: [
                { label: "测站坐标", prefix: "station" },
                { label: "后视点坐标", prefix: "backSight" }
            ],
            measurementColumns: [
                { key: "coordinateName", label: "测点", altKeys: ["point"] },
                { key: "x", label: "X", altKeys: ["coordinateX"] },
                { key: "y", label: "Y", altKeys: ["coordinateY"] },
                { key: "z", label: "Z", altKeys: ["coordinateZ"] }
            ]
        },
        displacement_level: {
            recordsTitle: "测点数据",
            maxRecordsPerPage: 22,
            headerFields: buildHeader([], null, {
                workCondition: { label: "工况" }
            }),
            recordColumns: [
                { key: "station", label: "测站" },
                { key: "point", label: "测点" },
                { key: "backSightReading", label: "后视读数（m）" },
                { key: "foreSightReading", label: "前视读数（m）" },
                { key: "elevation", label: "高程（m）" },
                { key: "note", label: "附注" }
            ]
        },
        alignment_total_station: {
            recordsTitle: "测点坐标",
            layout: "coordinate",
            maxRecordsPerPage: 9,
            headerFields: buildHeader(["workCondition"]),
            coordinateSections: [
                { label: "测站坐标", prefix: "station" },
                { label: "后视点坐标", prefix: "backSight" },
                { label: "校核点坐标", prefix: "checkPoint" }
            ],
            measurementColumns: [
                { key: "coordinateName", label: "测点", altKeys: ["point"] },
                { key: "x", label: "X", altKeys: ["coordinateX"] },
                { key: "y", label: "Y", altKeys: ["coordinateY"] },
                { key: "z", label: "Z", altKeys: ["coordinateZ"] }
            ]
        },
        alignment_level: {
            recordsTitle: "测点数据",
            maxRecordsPerPage: 20,
            headerFields: buildHeader(["workCondition"], [
                { key: "basePointElevation", label: "基准点高程（m）", col: 6, altKeys: ["benchmarkElevation"] }
            ]),
            recordColumns: [
                { key: "station", label: "测站" },
                { key: "point", label: "测点编号", altKeys: ["pointNumber"] },
                { key: "backSightReading", label: "后视读数（m）" },
                { key: "foreSightReading", label: "前视读数（m）" },
                { key: "elevation", label: "测量值（m）", altKeys: ["measuredElevation"] },
                { key: "designElevation", label: "修正值（m）", altKeys: ["correction"] },
                { key: "deviation", label: "修正后高程（m）", altKeys: ["correctedElevation"] },
                { key: "note", label: "备注" }
            ]
        },
        cable_force_vibration: {
            recordsTitle: "索力数据",
            layout: "vibration",
            maxRecordsPerPage: 11,
            headerFields: buildHeader(["workCondition"]),
            vibrationFields: {
                indexKey: "rowIndex",
                cableKey: "cableNo",
                summaryKey: "averageFrequency",
                summaryAltKeys: ["frequencySummary"],
                orderPrefix: "frequencyOrder",
                orderAltPrefix: "order",
                orderCount: 6,
                frequencyPrefix: "frequencyValue",
                frequencyAltPrefix: "frequency",
                frequencyCount: 6
            }
        },
        cable_force_sensor: {
            recordsTitle: "索力数据",
            maxRecordsPerPage: 19,
            headerFields: buildHeader([]),
            recordColumns: [
                { key: "cableNo", label: "索号" },
                { key: "sensorNumber", label: "传感器编号", altKeys: ["sensorNo", "point"] },
                { key: "testTemperature", label: "测试温度", altKeys: ["temperature"] },
                { key: "frequency", label: "频率（Hz）", altKeys: ["value1"] },
                { key: "measuredCableForce", label: "实测索力(kN)", altKeys: ["value2"] },
                { key: "theoreticalCableForce", label: "理论索力(kN)", altKeys: ["value3", "average"] },
                { key: "note", label: "附注" }
            ]
        },
        technical_condition: {
            recordsTitle: "缺损记录",
            headerFields: buildHeader(["workCondition"]),
            recordColumns: [
                { key: "position", label: "缺损位置", multiline: true, colWidth: "18%" },
                { key: "type", label: "缺损类型", multiline: true, colWidth: "18%" },
                { key: "quantity", label: "缺损数量", multiline: true, colWidth: "8%" },
                { key: "description", label: "病害描述（性质、范围、程度等）", multiline: true, colWidth: "25%" },
                { key: "level", label: "评定类别(1~5)", multiline: true, colWidth: "9%" },
                { key: "imgNoExp", label: "照片或图片（编号/时间）", multiline: true, colWidth: "14%", altKeys: ["photo"] }
            ]
        }
    };
})(window);
