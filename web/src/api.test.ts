import { describe, expect, it } from "vitest";
import { extractDouyinUrl } from "./api";

describe("extractDouyinUrl", () => {
  it("extracts a short Douyin URL from noisy share text", () => {
    expect(
      extractDouyinUrl("4.89 复制打开抖音 https://v.douyin.com/oSzS4bbF--I/ :1pm gOK:/")
    ).toBe("https://v.douyin.com/oSzS4bbF--I");
  });

  it("returns null when no URL exists", () => {
    expect(extractDouyinUrl("没有链接")).toBeNull();
  });
});

