package com.taktak.repository;
import com.taktak.model.RewardCampaign; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface RewardCampaignRepository extends JpaRepository<RewardCampaign,UUID>{Optional<RewardCampaign> findByCafeId(UUID cafeId);}
