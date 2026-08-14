package com.taktak.service;

import com.taktak.model.Coupon;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider; import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage; import org.springframework.mail.javamail.JavaMailSender; import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class CouponEmailService {
    private static final Logger log=LoggerFactory.getLogger(CouponEmailService.class);
    private final ObjectProvider<JavaMailSender> senders;
    @Value("${taktak.mail.from:}") private String from;
    public boolean send(Coupon c,String cafe){
        JavaMailSender sender=senders.getIfAvailable();
        if(sender==null||from==null||from.isBlank()){log.info("Coupon {} créé; SMTP non configuré",c.getCode());return false;}
        try{SimpleMailMessage m=new SimpleMailMessage();m.setFrom(from);m.setTo(c.getCustomerEmail());m.setSubject("Votre coupon "+cafe);m.setText("Vous avez reçu un coupon !\n\nGain : "+c.getRewardLabel()+"\nCode : "+c.getCode()+"\nValable jusqu'au "+c.getExpiresAt().toLocalDate()+".\n\nSaisissez ce code dans votre panier lors de votre prochaine commande.");sender.send(m);return true;}catch(Exception e){log.error("Coupon {} enregistré mais email non envoyé",c.getCode(),e);return false;}
    }
}
