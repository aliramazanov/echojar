package com.aliramazanov.echojar.bootstrap;

import com.aliramazanov.echojar.bootstrap.findings.SqlTemplate;

public interface SqlCarrier {

    SqlTemplate echojarTemplate();

    void echojarTemplate(SqlTemplate template);

    LeaseCarrier echojarOwner();

    void echojarOwner(LeaseCarrier owner);

    int echojarBatch();

    void echojarBatch(int pending);
}
