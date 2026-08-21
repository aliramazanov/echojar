package com.aliramazanov.echojar.bootstrap;

import java.util.List;

public interface StatementCarrier {

    List<String> echojarBatchSql();

    void echojarBatchSql(List<String> batch);
}
