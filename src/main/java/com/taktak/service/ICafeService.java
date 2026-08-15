package com.taktak.service;

import com.taktak.model.Cafe;
import com.taktak.model.CafeTable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ICafeService {
    List<Cafe> getAllCafes();
    Cafe getCafeBySlug(String slug);
    Map<String, Object> getMenuBySlug(String slug);
    List<CafeTable> getTablesByCafe(String slug);
    List<CafeTable> saveTablesBatch(String slug, List<CafeTable> tables);
    Map<String, String> uploadImage(MultipartFile file);
    Map<String, Object> getAnalytics(String slug);
    Cafe updateLocationSettings(String slug, Double latitude, Double longitude, Double geofenceRadiusMeters);
    CafeTable toggleTableGames(String slug, Integer tableNumber, Boolean enabled);
    Map<String, Object> getTableStatus(String slug, Integer tableNumber, String sessionToken);
}
