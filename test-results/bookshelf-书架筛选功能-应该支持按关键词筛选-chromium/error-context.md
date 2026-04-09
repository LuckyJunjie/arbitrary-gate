# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: bookshelf.spec.ts >> 书架筛选功能 >> 应该支持按关键词筛选
- Location: tests/e2e/bookshelf.spec.ts:207:3

# Error details

```
Error: page.waitForSelector: Target page, context or browser has been closed
Call log:
  - waiting for locator('[data-testid="story-card"]') to be visible

```

```
Error: write EPIPE
```