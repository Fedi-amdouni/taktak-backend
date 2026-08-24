package com.taktak.model;

public enum OrderPresenceStatus {
    VERIFIED_WIFI,      // Sur place connecté au WiFi du café
    VERIFIED_GPS,       // Sur place vérifié par géolocalisation GPS (< 120m)
    UNVERIFIED_LOCATION // 4G sans GPS ou distant (alerte discrète staff)
}
