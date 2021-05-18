package cn.lili.common.sms.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.lili.common.cache.Cache;
import cn.lili.common.cache.CachePrefix;
import cn.lili.common.exception.ServiceException;
import cn.lili.common.security.context.UserContext;
import cn.lili.common.sms.AliSmsUtil;
import cn.lili.common.sms.SmsUtil;
import cn.lili.common.utils.CommonUtil;
import cn.lili.common.verification.enums.VerificationEnums;
import cn.lili.modules.connect.util.Base64Utils;
import cn.lili.modules.member.entity.dos.Member;
import cn.lili.modules.member.service.MemberService;
import cn.lili.modules.message.entity.dos.SmsSign;
import cn.lili.modules.message.entity.dos.SmsTemplate;
import cn.lili.modules.system.entity.dos.Setting;
import cn.lili.modules.system.entity.dto.SmsSetting;
import cn.lili.modules.system.entity.enums.SettingEnum;
import cn.lili.modules.system.service.SettingService;
import com.aliyun.dysmsapi20170525.models.*;
import com.aliyun.teaopenapi.models.Config;
import com.google.gson.Gson;
import com.xkcoding.http.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 短信网管阿里云实现
 *
 * @author Chopper
 * @version v4.0
 * @Description:
 * @since 2020/11/30 15:44
 */
@Component
@Slf4j
public class SmsUtilAliImplService implements SmsUtil, AliSmsUtil {

    @Autowired
    private Cache cache;
    @Autowired
    private SettingService settingService;
    @Autowired
    private MemberService memberService;

    @Override
    public void sendSmsCode(String mobile, VerificationEnums verificationEnums, String uuid) {

        String code = CommonUtil.getRandomNum();

        switch (verificationEnums) {
            //如果某个模版需要自定义，则在此处进行调整
            case LOGIN:
            case REGISTER:
            case FIND_USER: {

                //准备发送短信参数
                Map<String, String> params = new HashMap<>();
                params.put("code", code);
                cache.put(cacheKey(verificationEnums, mobile, uuid), code, 300L);
                this.sendSmsCode("北京宏业汇成科技有限公司", mobile, params, "SMS_205755300");
                break;
            }
            case UPDATE_PASSWORD: {
                Member member = memberService.getById(UserContext.getCurrentUser().getId());
                if (member == null || StringUtil.isEmpty(member.getMobile())) {
                    return;
                }
                String memberMobile = member.getMobile();
                //准备发送短信参数
                Map<String, String> params = new HashMap<>();
                params.put("code", code);
                cache.put(cacheKey(verificationEnums, memberMobile, uuid), code, 300L);
                this.sendSmsCode("北京宏业汇成科技有限公司", mobile, params, "SMS_205755297");
                break;
            }
            //如果不是有效的验证码手段，则此处不进行短信操作
            default:
                return;
        }
    }

    @Override
    public boolean verifyCode(String mobile, VerificationEnums verificationEnums, String uuid, String code) {
        Object result = cache.get(cacheKey(verificationEnums, mobile, uuid));
        if (code.equals(result)) {
            //校验之后，删除
            cache.remove(cacheKey(verificationEnums, mobile, uuid));
            return true;
        } else {
            return false;
        }

    }

    @Override
    public void sendSmsCode(String signName, String mobile, Map<String, String> param, String templateCode) {

        com.aliyun.dysmsapi20170525.Client client = this.createClient();
        SendSmsRequest sendSmsRequest = new SendSmsRequest()
                .setSignName(signName)
                .setPhoneNumbers(mobile)
                .setTemplateCode(templateCode)
                .setTemplateParam(JSONUtil.toJsonStr(param));
        try {
            SendSmsResponse response = client.sendSms(sendSmsRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendBatchSms(String signName, List<String> mobile, String templateCode) {

        com.aliyun.dysmsapi20170525.Client client = this.createClient();

        List<String> sign = new ArrayList<String>();

        sign.addAll(mobile);
        sign.replaceAll(e -> signName);

        //手机号拆成多个小组进行发送
        List<List<String>> mobileList = new ArrayList<>();

        //签名名称多个小组
        List<List<String>> signNameList = new ArrayList<>();

        //循环分组
        for (int i = 0; i < (mobile.size() / 100 + (mobile.size() % 100 == 0 ? 0 : 1)); i++) {
            int endPoint = Math.min((100 + (i * 100)), mobile.size());
            mobileList.add(mobile.subList((i * 100), endPoint));
            signNameList.add(sign.subList((i * 100), endPoint));
        }

//        //发送短信
        for (int i = 0; i < mobileList.size(); i++) {
            SendBatchSmsRequest sendBatchSmsRequest = new SendBatchSmsRequest()
                    .setPhoneNumberJson(JSONUtil.toJsonStr(mobileList.get(i)))
                    .setSignNameJson(JSONUtil.toJsonStr(signNameList.get(i)))
                    .setTemplateCode(templateCode);
            try {
                client.sendBatchSms(sendBatchSmsRequest);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }


    @Override
    public void addSmsSign(SmsSign smsSign) throws Exception {
        //设置参数添加短信签名
        com.aliyun.dysmsapi20170525.Client client = this.createClient();
        System.out.println(smsSign.getBusinessLicense().substring(smsSign.getBusinessLicense().lastIndexOf(".") + 1));
        System.out.println(smsSign.getLicense().substring(smsSign.getLicense().lastIndexOf(".")));
        //营业执照
        AddSmsSignRequest.AddSmsSignRequestSignFileList signFileList0 = new AddSmsSignRequest.AddSmsSignRequestSignFileList()
                .setFileContents(Base64Utils.encode(smsSign.getBusinessLicense()))
                .setFileSuffix(smsSign.getBusinessLicense().substring(smsSign.getBusinessLicense().lastIndexOf(".") + 1));
        //授权委托书
        AddSmsSignRequest.AddSmsSignRequestSignFileList signFileList1 = new AddSmsSignRequest.AddSmsSignRequestSignFileList()
                .setFileContents(Base64Utils.encode(smsSign.getLicense()))
                .setFileSuffix(smsSign.getLicense().substring(smsSign.getLicense().lastIndexOf(".")) + 1);
        //添加短信签名
        AddSmsSignRequest addSmsSignRequest = new AddSmsSignRequest()
                .setSignName(smsSign.getSignName())
                .setSignSource(smsSign.getSignSource())
                .setRemark(smsSign.getRemark())
                .setSignFileList(java.util.Arrays.asList(
                        signFileList0,
                        signFileList1
                ));
        AddSmsSignResponse response = client.addSmsSign(addSmsSignRequest);
        if (!response.getBody().getCode().equals("OK")) {
            throw new ServiceException(response.getBody().getMessage());
        }
    }

    @Override
    public void deleteSmsSign(String signName) throws Exception {
        com.aliyun.dysmsapi20170525.Client client = this.createClient();
        DeleteSmsSignRequest deleteSmsSignRequest = new DeleteSmsSignRequest()
                .setSignName(signName);

        DeleteSmsSignResponse response = client.deleteSmsSign(deleteSmsSignRequest);
        if (!response.getBody().getCode().equals("OK")) {
            throw new ServiceException(response.getBody().getMessage());
        }

    }

    @Override
    public Map<String, Object> querySmsSign(String signName) throws Exception {
        //设置参数查看短信签名
        com.aliyun.dysmsapi20170525.Client client = this.createClient();
        QuerySmsSignRequest querySmsSignRequest = new QuerySmsSignRequest().setSignName(signName);

        QuerySmsSignResponse response = client.querySmsSign(querySmsSignRequest);
        if (!response.getBody().getCode().equals("OK")) {
            throw new ServiceException(response.getBody().getMessage());
        }
        Map<String, Object> map = new HashMap<>();
        map.put("SignStatus", response.getBody().getSignStatus());
        map.put("Reason", response.getBody().getReason());
        return map;
    }

    @Override
    public void modifySmsSign(SmsSign smsSign) throws Exception {
        //设置参数添加短信签名
        com.aliyun.dysmsapi20170525.Client client = this.createClient();

        ModifySmsSignRequest.ModifySmsSignRequestSignFileList signFileList0 = new ModifySmsSignRequest.ModifySmsSignRequestSignFileList()
                .setFileContents(Base64Utils.encode(smsSign.getBusinessLicense()))
                .setFileSuffix(smsSign.getBusinessLicense().substring(smsSign.getBusinessLicense().lastIndexOf(".") + 1));
        ModifySmsSignRequest.ModifySmsSignRequestSignFileList signFileList1 = new ModifySmsSignRequest.ModifySmsSignRequestSignFileList()
                .setFileContents(Base64Utils.encode(smsSign.getLicense()))
                .setFileSuffix(smsSign.getLicense().substring(smsSign.getBusinessLicense().lastIndexOf(".") + 1));
        ModifySmsSignRequest modifySmsSign = new ModifySmsSignRequest()
                .setSignName(smsSign.getSignName())
                .setSignSource(smsSign.getSignSource())
                .setRemark(smsSign.getRemark())
                .setSignFileList(java.util.Arrays.asList(
                        signFileList0,
                        signFileList1
                ));
        ModifySmsSignResponse response = client.modifySmsSign(modifySmsSign);
        if (!response.getBody().getCode().equals("OK")) {
            throw new ServiceException(response.getBody().getMessage());
        }
    }

    @Override
    public void modifySmsTemplate(SmsTemplate smsTemplate) throws Exception {
        com.aliyun.dysmsapi20170525.Client client = this.createClient();
        ModifySmsTemplateRequest modifySmsTemplateRequest = new ModifySmsTemplateRequest()
                .setTemplateType(smsTemplate.getTemplateType())
                .setTemplateName(smsTemplate.getTemplateName())
                .setTemplateContent(smsTemplate.getTemplateContent())
                .setRemark(smsTemplate.getRemark())
                .setTemplateCode(smsTemplate.getTemplateCode());

        ModifySmsTemplateResponse response = client.modifySmsTemplate(modifySmsTemplateRequest);
        if (!response.getBody().getCode().equals("OK")) {
            throw new ServiceException(response.getBody().getMessage());
        }
    }

    @Override
    public Map<String, Object> querySmsTemplate(String templateCode) throws Exception {
        com.aliyun.dysmsapi20170525.Client client = this.createClient();
        QuerySmsTemplateRequest querySmsTemplateRequest = new QuerySmsTemplateRequest()
                .setTemplateCode(templateCode);
        QuerySmsTemplateResponse response = client.querySmsTemplate(querySmsTemplateRequest);

        if (!response.getBody().getCode().equals("OK")) {
            throw new ServiceException(response.getBody().getMessage());
        }
        Map<String, Object> map = new HashMap<>();
        map.put("TemplateStatus", response.getBody().getTemplateStatus());
        map.put("Reason", response.getBody().getReason());
        map.put("TemplateCode", response.getBody().getTemplateCode());
        return map;
    }

    @Override
    public String addSmsTemplate(SmsTemplate smsTemplate) throws Exception {
        com.aliyun.dysmsapi20170525.Client client = this.createClient();
        AddSmsTemplateRequest addSmsTemplateRequest = new AddSmsTemplateRequest()
                .setTemplateType(1)
                .setTemplateName(smsTemplate.getTemplateName())
                .setTemplateContent(smsTemplate.getTemplateContent())
                .setRemark(smsTemplate.getRemark());

        AddSmsTemplateResponse response = client.addSmsTemplate(addSmsTemplateRequest);
        if (!response.getBody().getCode().equals("OK")) {
            throw new ServiceException(response.getBody().getMessage());
        }
        return response.getBody().getTemplateCode();
    }

    @Override
    public void deleteSmsTemplate(String templateCode) throws Exception {
        com.aliyun.dysmsapi20170525.Client client = this.createClient();
        DeleteSmsTemplateRequest deleteSmsTemplateRequest = new DeleteSmsTemplateRequest()
                .setTemplateCode(templateCode);

        DeleteSmsTemplateResponse response = client.deleteSmsTemplate(deleteSmsTemplateRequest);
        if (!response.getBody().getCode().equals("OK")) {
            throw new ServiceException(response.getBody().getMessage());
        }
    }


    /**
     * 使用AK&SK初始化账号Client
     *
     * @return Client
     * @throws Exception
     */
    public com.aliyun.dysmsapi20170525.Client createClient() {
        try {
            Setting setting = settingService.getById(SettingEnum.SMS_SETTING.name());
            if (StrUtil.isBlank(setting.getSettingValue())) {
                throw new ServiceException("您还未配置阿里云短信");
            }
            SmsSetting smsSetting = new Gson().fromJson(setting.getSettingValue(), SmsSetting.class);

            Config config = new Config();
            // 您的AccessKey ID
            //config.accessKeyId = smsSetting.getAccessKeyId();
            config.accessKeyId = "LTAI4G4deX59EyjpEULaJdsU";
            // 您的AccessKey Secret
            //config.accessKeySecret = smsSetting.getAccessSecret();
            config.accessKeySecret = "BlRBpl7WBman6GYYwLKMiKqMTXFhWf";
            // 访问的域名
            config.endpoint = "dysmsapi.aliyuncs.com";
            return new com.aliyun.dysmsapi20170525.Client(config);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 生成缓存key
     *
     * @param verificationEnums 验证场景
     * @param mobile            手机号码
     * @param uuid              用户标识 uuid
     * @return
     */
    static String cacheKey(VerificationEnums verificationEnums, String mobile, String uuid) {
        return CachePrefix.SMS_CODE.getPrefix() + verificationEnums.name() + mobile;
    }
}
