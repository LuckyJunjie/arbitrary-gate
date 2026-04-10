# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: bookshelf.spec.ts >> 故事卡操作 >> 应该可以删除故事
- Location: tests/e2e/bookshelf.spec.ts:316:3

# Error details

```
Error: locator.click: Target page, context or browser has been closed
Call log:
  - waiting for locator('[data-testid="story-card"]').first().locator('[data-testid="card-menu-button"]')

```

```
Error: write EPIPE
```