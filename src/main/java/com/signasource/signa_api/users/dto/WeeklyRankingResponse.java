package com.signasource.signa_api.users.dto;

import java.util.List;

public record WeeklyRankingResponse(
        List<RankingEntryResponse> entries,
        int total,
        MyRankingPositionResponse me) {}
