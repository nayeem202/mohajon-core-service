package com.haalkhata.feature.report.application;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReportTable {

    private final String title;
    private final String subtitle;
    private final List<ReportColumn> columns;
    private final List<Map<String, Object>> rows;
}