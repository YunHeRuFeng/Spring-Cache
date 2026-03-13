package com.playground.cache.web;

import com.playground.cache.core.CachePolicyType;
import com.playground.cache.service.CacheManagerService;
import com.playground.cache.service.ProductQueryResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
public class DashboardController {

    private final CacheManagerService cacheManagerService;

    public DashboardController(CacheManagerService cacheManagerService) {
        this.cacheManagerService = cacheManagerService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("products", cacheManagerService.allProducts());
        model.addAttribute("entries", cacheManagerService.entries());
        model.addAttribute("hotKeys", cacheManagerService.hotKeys(5));
        model.addAttribute("stats", cacheManagerService.stats());
        model.addAttribute("currentPolicy", cacheManagerService.policyName());
        model.addAttribute("capacity", cacheManagerService.capacity());
        model.addAttribute("size", cacheManagerService.size());
        model.addAttribute("lastCleanupCount", cacheManagerService.lastCleanupCount());
        model.addAttribute("lastCleanupAt", cacheManagerService.lastCleanupAt());
        model.addAttribute("lastQuerySource", cacheManagerService.lastQuerySource());
        model.addAttribute("lastQueryKey", cacheManagerService.lastQueryKey());
        model.addAttribute("policies", CachePolicyType.values());
        return "dashboard";
    }

    @PostMapping("/policy")
    public String switchPolicy(@RequestParam CachePolicyType policy, RedirectAttributes redirectAttributes) {
        cacheManagerService.switchPolicy(policy);
        redirectAttributes.addFlashAttribute("message", "已切换为 " + policy + " 策略");
        return "redirect:/";
    }

    @PostMapping("/products/query")
    public String queryProduct(@RequestParam Long productId, RedirectAttributes redirectAttributes) {
        ProductQueryResult result = cacheManagerService.queryProductWithSource(productId);
        if (result.product() != null) {
            redirectAttributes.addFlashAttribute("message", "已查询商品 #" + productId + "，" + result.source().label());
        } else {
            redirectAttributes.addFlashAttribute("message", "未找到商品 #" + productId);
        }
        return "redirect:/";
    }

    @PostMapping("/cache/remove/{key}")
    public String removeCache(@PathVariable Long key, RedirectAttributes redirectAttributes) {
        if (cacheManagerService.remove(key)) {
            redirectAttributes.addFlashAttribute("message", "已删除缓存键 #" + key);
        } else {
            redirectAttributes.addFlashAttribute("message", "缓存中不存在键 #" + key);
        }
        return "redirect:/";
    }

    @PostMapping("/cache/clear")
    public String clearCache(RedirectAttributes redirectAttributes) {
        cacheManagerService.clear();
        redirectAttributes.addFlashAttribute("message", "已清空所有缓存条目");
        return "redirect:/";
    }
}
