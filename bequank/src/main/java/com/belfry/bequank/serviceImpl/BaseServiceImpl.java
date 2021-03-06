package com.belfry.bequank.serviceImpl;

import com.belfry.bequank.entity.primary.User;
import com.belfry.bequank.exception.AuthorityException;
import com.belfry.bequank.exception.DuplicateUserException;
import com.belfry.bequank.repository.primary.UserRepository;
import com.belfry.bequank.service.BaseService;
import com.belfry.bequank.util.JwtUtil;
import com.belfry.bequank.util.Message;
import com.belfry.bequank.util.Role;
import com.belfry.bequank.util.TutorialType;
import com.sun.mail.util.MailSSLSocketFactory;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@Service
public class BaseServiceImpl implements BaseService {

    @Resource
    UserRepository repository;

    @Autowired
    StringRedisTemplate template;

    @Autowired
    JwtUtil jwtUtil;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public JSONObject register(JSONObject object) {
        JSONObject res = new JSONObject();
        String userName = object.getString("email");
        User user1 = repository.findByUserName(userName);
        if (user1 != null) {
            throw new DuplicateUserException();
        }

        String actualCode = object.getString("identifyCode");
        String expectCode = template.opsForValue().get("codeList::" + userName);
        if (!actualCode.equals(expectCode)) {
            res.put("status", Message.MSG_WRONG_VERICODE);
            res.put("message", "验证码错误");
            return res;
        }

        String password = object.getString("password");
        String nickName = object.getString("nickname");

        SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd");
        User user = new User(userName, password, nickName, null, null, null, null, null, null, null, null, Role.NORMAL, time.format(new Date()), 0.0, 0.0, 0, 0, false, TutorialType.BEGINNER, false, false);
        repository.saveAndFlush(user);
        res.put("status", Message.MSG_SUCCESS);
        res.put("message", "注册成功");
        return res;
    }

    @Override
    public JSONObject login(User user) {
        JSONObject object = new JSONObject();
        User user1 = repository.findByUserName(user.getUserName());
        if (user1 == null) {
            throw new AuthorityException();
        }

        String expectPw = user1.getPassword();
        String actualPw = user.getPassword();
        if (!expectPw.equals(actualPw)) {
            throw new AuthorityException();
        }

        String token = jwtUtil.generateToken(user1);
        String role = user1.getRole();
        object.put("status", Message.MSG_SUCCESS);
        object.put("token", token);
        object.put("role", role);
        return object;
    }

    @Override
    @CacheEvict(value = "loginList", key = "#user.userName")
    public void logout(User user) {
        logger.info("user {} logs out", user.getUserName());
    }

    @Override
    public JSONObject sendVerificationCode(String email) throws GeneralSecurityException, MessagingException {
        Properties props = new Properties();

        //同一个bean的内部方法调用不会触发cache！因此要显式使用cache
        int code = (int) (Math.random() * 900000 + 100000);
        template.opsForValue().set("codeList::" + email, Integer.toString(code));

        // 发送服务器需要身份验证
        props.setProperty("mail.smtp.auth", "true");
        // 设置邮件服务器主机名
        props.setProperty("mail.host", "smtp.qq.com");
        // 发送邮件协议名称
        props.setProperty("mail.transport.protocol", "smtp");

        MailSSLSocketFactory ssl = new MailSSLSocketFactory();
        ssl.setTrustAllHosts(true);
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.ssl.socketFactory", ssl);

        Session session = Session.getInstance(props);

        MimeMessage message = new MimeMessage(session);
        message.setSubject("您的beQuank金融平台验证码");
        message.setFrom(new InternetAddress("498924217@qq.com"));


        String pre = "<p>您请求的beQuank金融平台邮箱验证服务验证码为：<font size=\"5\" color=\"red\"><b>";
        String suf = "</b></font>，如非本人操作，请忽视本信息，并尽快修改您的密码。</p>" +
                "<p>请勿将此验证码告知他人。</p><p>belfry团队</p>";

        MimeMultipart all = new MimeMultipart("related");
        MimeBodyPart text = new MimeBodyPart();
        text.setContent(pre + code + suf, "text/html;charset=UTF-8");
        all.addBodyPart(text);

        message.setContent(all);

        Transport transport = session.getTransport();
        transport.connect("smtp.qq.com", "498924217@qq.com", "bmtdvhahtfcebjfj");

        transport.sendMessage(message, new Address[]{new InternetAddress(email)});
        transport.close();
        JSONObject object = new JSONObject();
        object.put("status", Message.MSG_SUCCESS);
        object.put("message", "发送成功");
        return object;
    }

    /**
     * 获取用户权限列表
     * @author Mr.Wang
     * @param userId userId
     * @return net.sf.json.JSONObject
     */
    @Override
    public JSONObject getAuth(long userId) {
        JSONObject object = new JSONObject();
        User userModel = repository.getById(userId);
        if (userModel == null) {
            object.put("status", Message.MSG_FAILED);
        } else {
            object.put("hasSigned", userModel.isHasSignedToday());
            if (userModel.getTutorialType() == null) {
                object.put("courses", TutorialType.BEGINNER);
                userModel.setTutorialType(TutorialType.BEGINNER);
                repository.saveAndFlush(userModel);
            } else {
                object.put("courses", userModel.getTutorialType());
            }
            object.put("ratioTrend", userModel.isRatioTrend());
            object.put("trend", userModel.isTrend());
        }
        return object;
    }

    /**
     * TODO: 需要增加每天把用户设为没签到的状态
     * 每日签到
     * @author Mr.Wang
     * @param userId userId
     * @return JSONObject
     */
    @Override
    public JSONObject dailySign(long userId) {
        User userModel = repository.getById(userId);
        if (userModel != null) {
            userModel.setHasSignedToday(true);
            int oldExp = userModel.getExp();
            oldExp += 2;
            userModel.setExp(oldExp);
            repository.saveAndFlush(userModel);
        }
        return new JSONObject();
    }

    /**
     * 每天0:01触发, 消除已经签到
     * @author Mr.Wang
     * @param () null
     */
    @Scheduled(cron = "0 01 0 ? * *")
    public void signRecover() {
        List<User> list = repository.findAll();
        list.forEach(user -> {
            user.setHasSignedToday(false);
            repository.saveAndFlush(user);
        });
    }

    /**
     * 解锁功能
     * @author Mr.Wang
     * @param userId userId
     * @param object object
     * @return net.sf.json.JSONObject
     */
    @Override
    public JSONObject unlockFunction(long userId, JSONObject object) {
        JSONObject result = new JSONObject();
        User user = repository.getById(userId);
        if (user != null) {
            String type = object.getString("type");
            if (type.equals("Premium")) {
                user.setRatioTrend(true);
                user.setTrend(false);
                user.setTutorialType(TutorialType.INTERMEDIATE);
                int coins = user.getCoins();
                if (coins < 49) {
                    result.put("success", false);
                    return result;
                }
                coins -= 49;
                user.setCoins(coins);
            } else {
                user.setRatioTrend(true);
                user.setTrend(true);
                user.setTutorialType(TutorialType.ADVANCED);
                int coins = user.getCoins();
                if (coins < 99) {
                    result.put("success", false);
                    return result;
                }
                coins -= 99;
                user.setCoins(coins);
            }
            repository.saveAndFlush(user);
            result.put("success", true);
            return result;
        }
        result.put("success", false);
        return result;
    }

    /**
     * 金币解锁课程
     * @author Mr.Wang
     * @param userId userId
     * @param object JSONObject
     * @return net.sf.json.JSONObject
     */
    @Override
    public JSONObject unlockCourse(long userId, JSONObject object) {
        JSONObject result = new JSONObject();
        User user = repository.getById(userId);
        if (user != null) {
            String type = object.getString("type");
            if (type.equals("INTERMEDIATE") || type.equals("ADVANCED")) {
                user.setTutorialType(type);
                repository.saveAndFlush(user);
                result.put("success", true);
                return result;
            }
        }
        result.put("success", false);
        return result;
    }

    /**
     * 获取用户个人信息
     * @author YYQ->Mr.Wang
     * @param userId userId
     * @return net.sf.json.JSONObject
     */
    @Override
    public JSONObject getProfile(long userId) {
        //return repository.getById(userId);
        JSONObject object = new JSONObject();
        User userModel = repository.getById(userId);
        if (userModel == null) {
            object.put("status", Message.MSG_FAILED);
        } else {
            object.put("nickname", userModel.getNickname());
            object.put("id", userModel.getId());
            object.put("avatar", userModel.getAvatar());
            object.put("phone", userModel.getPhone());
            object.put("email", userModel.getEmail());
            object.put("gender", userModel.getGender());
            object.put("birthday", userModel.getBirthday());
            object.put("moneyLevel", userModel.getMoneyLevel());
            object.put("bio", userModel.getBio());
            object.put("level", userModel.getLevel());
            object.put("registerTime", userModel.getRegisterTime());
            object.put("expectedProfit", userModel.getExpectedProfit());
            object.put("riskAbility", userModel.getRiskAbility());
            object.put("coins", userModel.getCoins());
            object.put("exp", userModel.getExp());
        }
        return object;
    }

    @Override
    public JSONObject setProfile(long userId, JSONObject user) {
        JSONObject object = new JSONObject();
        User user1 = repository.getById(userId);
        if (user1 == null) {
            object.put("status", Message.MSG_FAILED);
        } else {
            user1.setNickname(user.optString("nickname"));
            user1.setAvatar(user.optString("avatar"));
            user1.setPhone(user.optString("phone"));
            user1.setGender(user.optString("gender"));
            user1.setBirthday(user.optString("birthday"));
            user1.setMoneyLevel(user.optString("moneyLevel"));
            user1.setBio(user.optString("bio"));
            user1.setExpectedProfit(Double.parseDouble(user.optString("expectedProfit")));
            repository.saveAndFlush(user1);
            object.put("status", Message.MSG_SUCCESS);
        }

        return object ;
    }

    @Override
    public JSONObject setPassword(long userId, JSONObject object) {
        User user = repository.getById(userId);
        JSONObject res = new JSONObject();
        if (user == null||!user.getPassword().equals(object.getString("oriPassword"))) {
            res.put("status", Message.MSG_FAILED);
        } else {
            user.setPassword(object.getString("newPassword"));
            repository.saveAndFlush(user);
            res.put("status", Message.MSG_SUCCESS);
        }
        return res;
    }

//    @Override
//    public JSONObject getProfile(User user) {
//        JSONObject res = new JSONObject();
//
//        if (repository.findByUserName(user.getUserName()) == null) {
//            res.put("status", Message.MSG_USER_NOTEXIST);
//            res.put("message", "用户不存在");
//            return res;
//        }
//
//        res.put("nickname", user.getNickname());
//        res.put("avatar", user.getAvatar());
//        res.put("phone", user.getPhone());
//        res.put("email", user.getEmail());
//        res.put("gender", user.getGender());
//        res.put("birthday", user.getBirthday());
//        res.put("moneyLevel", user.getMoneyLevel());
//        res.put("bio", user.getBio());
//        res.put("registerTime", user.getRegisterTime());
//
//        return res;
//    }

//    @Override
//    public JSONObject setProfile(User user, JSONObject object) {
//        JSONObject res = new JSONObject();
//
//        // TODO: 18-8-29 Response Code, how to check if revision is successful?
//        res.put("status", Message.MSG_SUCCESS);
//
//        repository.setProfile(user.getUserName(), object.getString("nickname"), object.getString("avatar"), object.getString("phone"), object.getString("email"), object.getString("gender"), object.getString("birthday"), object.getString("moneyLevel"), object.getString("bio"));
//
//        return res;
//    }
//
//    @Override
//    public JSONObject setPassword(User user, JSONObject object) {
//        JSONObject res = new JSONObject();
//
//        // TODO: 18-8-29 Response Code, how to check if revision is successful?
//        res.put("status", Message.MSG_SUCCESS);
//        User u = repository.findByUserName(user.getUserName());
//        if (!u.getPassword().equals(object.getString("oriPassword"))){
//            res.put("status",Message.MSG_WRONG_PASSWORD);
//            return res;
//        }
//
//        repository.setPassword(user.getUserName(), object.getString("newPassword"));
//
//        return res;
//    }

}
