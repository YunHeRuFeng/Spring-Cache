from __future__ import annotations

from pathlib import Path
from typing import Iterable

from selenium import webdriver
from selenium.webdriver.chrome.options import Options


ROOT = Path(__file__).resolve().parents[1]
DIAGRAM_DIR = ROOT / "docs" / "diagrams"
OUTPUT_DIR = DIAGRAM_DIR / "images"

TARGETS: Iterable[tuple[str, str]] = (
    ("01-architecture.html", "01-architecture.png"),
    ("02-request-lifecycle.html", "02-request-lifecycle.png"),
    ("03-lru-policy.html", "03-lru-policy.png"),
    ("04-lfu-policy.html", "04-lfu-policy.png"),
    ("05-fifo-policy.html", "05-fifo-policy.png"),
)


def make_driver() -> webdriver.Chrome:
    options = Options()
    options.add_argument("--headless=new")
    options.add_argument("--disable-gpu")
    options.add_argument("--hide-scrollbars")
    options.add_argument("--force-device-scale-factor=2")
    options.add_argument("--window-size=1920,1080")
    options.add_argument("--allow-file-access-from-files")
    options.add_argument("--disable-web-security")
    return webdriver.Chrome(options=options)


def screenshot_full_page(driver: webdriver.Chrome, html_path: Path, output_path: Path) -> None:
    url = html_path.resolve().as_uri()
    driver.get(url)

    # 获取页面的完整宽高（兼容 body / documentElement）
    width = driver.execute_script(
        """
        return Math.max(
            document.body.scrollWidth,
            document.documentElement.scrollWidth,
            document.body.offsetWidth,
            document.documentElement.offsetWidth,
            document.documentElement.clientWidth
        );
        """
    )
    height = driver.execute_script(
        """
        return Math.max(
            document.body.scrollHeight,
            document.documentElement.scrollHeight,
            document.body.offsetHeight,
            document.documentElement.offsetHeight,
            document.documentElement.clientHeight
        );
        """
    )

    width = max(int(width), 1200)
    height = max(int(height), 900)
    driver.set_window_size(width, height)

    # 再次读取，避免 set_window_size 后重新布局带来的高度变化
    height_after = driver.execute_script(
        """
        return Math.max(
            document.body.scrollHeight,
            document.documentElement.scrollHeight,
            document.body.offsetHeight,
            document.documentElement.offsetHeight,
            document.documentElement.clientHeight
        );
        """
    )
    driver.set_window_size(width, max(int(height_after), height))

    output_path.parent.mkdir(parents=True, exist_ok=True)
    if not driver.save_screenshot(str(output_path)):
        raise RuntimeError(f"保存截图失败: {output_path}")


def main() -> None:
    missing = [name for name, _ in TARGETS if not (DIAGRAM_DIR / name).exists()]
    if missing:
        raise FileNotFoundError(f"缺少 HTML 文件: {missing}")

    driver = make_driver()
    try:
        for html_name, png_name in TARGETS:
            html_path = DIAGRAM_DIR / html_name
            output_path = OUTPUT_DIR / png_name
            screenshot_full_page(driver, html_path, output_path)
            print(f"[OK] {html_name} -> {output_path.relative_to(ROOT)}")
    finally:
        driver.quit()


if __name__ == "__main__":
    main()
