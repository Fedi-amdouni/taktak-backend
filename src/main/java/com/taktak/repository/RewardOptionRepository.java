package com.taktak.repository;
import com.taktak.model.RewardOption; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface RewardOptionRepository extends JpaRepository<RewardOption,UUID>{List<RewardOption> findByCampaignIdAndEnabledTrue(UUID campaignId); List<RewardOption> findByCampaignIdOrderByDiscountPercentAsc(UUID campaignId); void deleteByCampaignId(UUID campaignId);}
