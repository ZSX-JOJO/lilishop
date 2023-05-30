package cn.lili.common.token.base.generate;

import cn.lili.common.exception.ServiceException;
import cn.lili.common.security.AuthUser;
import cn.lili.common.security.enums.UserEnums;
import cn.lili.common.token.Token;
import cn.lili.common.token.TokenUtil;
import cn.lili.common.token.base.AbstractTokenGenerate;
import cn.lili.common.enums.SwitchEnum;
import cn.lili.modules.member.entity.dos.Member;
import cn.lili.modules.member.service.MemberService;
import cn.lili.modules.store.entity.dos.Store;
import cn.lili.modules.store.service.StoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 商家token生成
 *
 * @author Chopper
 * @version v4.0
 * @Description:
 * @since 2020/11/16 10:51
 */
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class StoreTokenGenerate extends AbstractTokenGenerate {

    private MemberService memberService;

    private StoreService storeService;

    private final TokenUtil tokenUtil;

    @Autowired
    public void setMemberService(MemberService memberService) {
        this.memberService = memberService;
    }

    @Autowired
    public void setStoreService(StoreService storeService) {
        this.storeService = storeService;
    }

    @Override
    public Token createToken(String username, Boolean longTerm) {
        // 生成token
        Member member = memberService.findByUsername(username);
        if (member.getHaveStore().equals(SwitchEnum.CLOSE.name())) {
            throw new ServiceException("该会员未开通店铺");
        }
        AuthUser user = new AuthUser(member.getUsername(), member.getId(),member.getNickName(), UserEnums.STORE);
        LambdaQueryWrapper<Store> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Store::getMemberId, member.getId());
        Store store = storeService.getOne(queryWrapper);
        user.setStoreId(store.getId());
        user.setStoreName(store.getStoreName());
        return tokenUtil.createToken(username, user, longTerm, UserEnums.STORE);
    }

    @Override
    public Token refreshToken(String refreshToken) {
        return tokenUtil.refreshToken(refreshToken, UserEnums.STORE);
    }

}