package com.haalkhata.feature.report.application;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReportColumn {

    private final String key;
    private final String header;
    private final int width;
    private final boolean rightAlign;
}