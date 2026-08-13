package com.taktak.service;

import com.taktak.dto.WaiterDTO;

import java.util.List;

public interface IWaiterService {
    List<WaiterDTO> getActiveWaiters(String cafeSlug);
    WaiterDTO loginByPin(String cafeSlug, String pinCode);
    WaiterDTO assignTables(String waiterIdStr, List<Integer> tableNumbers);
    WaiterDTO createWaiter(String cafeSlug, String name, String pinCode, String shiftHours);
    WaiterDTO updateWaiter(String waiterIdStr, String name, String pinCode, String shiftHours, Boolean isActive);
    void deleteWaiter(String waiterIdStr);
}
