package com.playground.cache.service;

import com.playground.cache.model.Product;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ProductRepository {

    private final Map<Long, Product> storage = new HashMap<>();

    @PostConstruct
    void init() {
        storage.put(1L, new Product(1L, "机械键盘", "外设", new BigDecimal("399.00"), "支持热插拔的 75% 机械键盘"));
        storage.put(2L, new Product(2L, "降噪耳机", "音频", new BigDecimal("899.00"), "支持自适应降噪与 40 小时续航"));
        storage.put(3L, new Product(3L, "便携式固态硬盘", "存储", new BigDecimal("529.00"), "支持 USB4 的高速便携固态"));
        storage.put(4L, new Product(4L, "4K 显示器", "显示", new BigDecimal("1599.00"), "27 英寸 IPS 编程显示器"));
        storage.put(5L, new Product(5L, "人体工学鼠标", "外设", new BigDecimal("269.00"), "支持多设备切换的人体工学鼠标"));
        storage.put(6L, new Product(6L, "桌面麦克风", "音频", new BigDecimal("459.00"), "适合会议和录音的 USB 麦克风"));
        storage.put(7L, new Product(7L, "USB-C 扩展坞", "配件", new BigDecimal("329.00"), "支持双屏输出与千兆网口"));
        storage.put(8L, new Product(8L, "编程台灯", "办公", new BigDecimal("199.00"), "支持无频闪调光与色温切换"));
        storage.put(9L, new Product(9L, "轻薄笔记本支架", "办公", new BigDecimal("119.00"), "铝合金可折叠散热支架"));
        storage.put(10L, new Product(10L, "无线数字键盘", "外设", new BigDecimal("159.00"), "适合财务和数据录入的无线数字键盘"));
        storage.put(11L, new Product(11L, "便携投影仪", "显示", new BigDecimal("1999.00"), "支持自动对焦和 1080P 输出"));
        storage.put(12L, new Product(12L, "蓝牙音箱", "音频", new BigDecimal("349.00"), "双单元立体声蓝牙音箱"));
        storage.put(13L, new Product(13L, "机械轴体测试器", "外设", new BigDecimal("89.00"), "用于比较不同机械轴手感"));
        storage.put(14L, new Product(14L, "显示器挂灯", "办公", new BigDecimal("239.00"), "减少屏幕反光的显示器挂灯"));
        storage.put(15L, new Product(15L, "程序员午睡枕", "办公", new BigDecimal("79.00"), "适合工位短暂休息的记忆棉靠枕"));
        storage.put(16L, new Product(16L, "高刷便携屏", "显示", new BigDecimal("999.00"), "支持 144Hz 刷新的便携副屏"));
        storage.put(17L, new Product(17L, "NAS 入门硬盘", "存储", new BigDecimal("649.00"), "适合家庭实验室的 4TB 机械硬盘"));
        storage.put(18L, new Product(18L, "静音风扇散热底座", "配件", new BigDecimal("179.00"), "适合笔记本长时间编译场景"));
        storage.put(19L, new Product(19L, "无线演示器", "配件", new BigDecimal("139.00"), "支持翻页与激光指示"));
        storage.put(20L, new Product(20L, "开发板套件", "硬件", new BigDecimal("599.00"), "带传感器模块的嵌入式开发入门套件"));
    }

    public Optional<Product> findById(Long id) {
        simulateSlowQuery();
        return Optional.ofNullable(storage.get(id));
    }

    public List<Product> findAll() {
        return storage.values().stream()
                .sorted(Comparator.comparing(Product::id))
                .toList();
    }

    private void simulateSlowQuery() {
        try {
            Thread.sleep(600);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
