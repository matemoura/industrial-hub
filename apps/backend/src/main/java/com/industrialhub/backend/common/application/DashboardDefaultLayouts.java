package com.industrialhub.backend.common.application;

public final class DashboardDefaultLayouts {

    private static final String OPERATOR_DEFAULT =
        "[" +
        "{\"id\":\"w1\",\"type\":\"oee-avg\",\"column\":1,\"row\":1}," +
        "{\"id\":\"w2\",\"type\":\"nc-open\",\"column\":2,\"row\":1}," +
        "{\"id\":\"w3\",\"type\":\"nc-critical\",\"column\":3,\"row\":1}," +
        "{\"id\":\"w4\",\"type\":\"wo-open\",\"column\":1,\"row\":2}," +
        "{\"id\":\"w5\",\"type\":\"mttr\",\"column\":2,\"row\":2}," +
        "{\"id\":\"w6\",\"type\":\"equipment-count\",\"column\":3,\"row\":2}" +
        "]";

    private static final String SUPERVISOR_DEFAULT =
        "[" +
        "{\"id\":\"w1\",\"type\":\"oee-avg\",\"column\":1,\"row\":1}," +
        "{\"id\":\"w2\",\"type\":\"nc-open\",\"column\":2,\"row\":1}," +
        "{\"id\":\"w3\",\"type\":\"nc-critical\",\"column\":3,\"row\":1}," +
        "{\"id\":\"w4\",\"type\":\"wo-open\",\"column\":1,\"row\":2}," +
        "{\"id\":\"w5\",\"type\":\"mttr\",\"column\":2,\"row\":2}," +
        "{\"id\":\"w6\",\"type\":\"equipment-count\",\"column\":3,\"row\":2}," +
        "{\"id\":\"w7\",\"type\":\"oee-trend\",\"column\":1,\"row\":3}," +
        "{\"id\":\"w8\",\"type\":\"nc-pareto\",\"column\":2,\"row\":3}" +
        "]";

    private DashboardDefaultLayouts() {}

    public static String forRole(String role) {
        if ("SUPERVISOR".equals(role) || "ADMIN".equals(role)) {
            return SUPERVISOR_DEFAULT;
        }
        return OPERATOR_DEFAULT;
    }
}
