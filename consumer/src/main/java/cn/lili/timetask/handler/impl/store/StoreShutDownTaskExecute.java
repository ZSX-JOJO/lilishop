package cn.lili.timetask.handler.impl.store;

import cn.lili.modules.store.service.StoreService;
import cn.lili.timetask.handler.EveryMinuteExecute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 店铺自动关店（每分钟执行）
 *
 * @author paulG
 * @since 2021/9/10
 **/
@Slf4j
@Component
public class StoreShutDownTaskExecute implements EveryMinuteExecute {

    @Autowired
    private StoreService storeService;

    /**
     * 执行
     */
    @Override
    public void execute() {
        storeService.autoShutdown();
        storeService.autoOpen();
    }
}
