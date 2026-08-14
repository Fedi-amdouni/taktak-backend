package com.taktak.repository;
import com.taktak.model.CustomerFeedback; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface CustomerFeedbackRepository extends JpaRepository<CustomerFeedback,UUID>{boolean existsByOrderId(UUID orderId);boolean existsByCafeIdAndCustomerEmailIgnoreCaseAndCreatedAtAfter(UUID cafeId,String email,java.time.LocalDateTime cutoff);}
