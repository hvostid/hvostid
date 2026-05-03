[English](./README.md) | **Русский**

# Frontend

React 18 single-page приложение, в дев-режиме раздаваемое через Vite,
а в проде через Nginx.

## Зоны ответственности

- Auth-флоу (регистрация, login, logout, профиль).
- Просмотр каталога и страница объявления.
- Страницы продавца (создание / редактирование / список своих
  объявлений).
- Анкета покупателя и результаты подбора.
- Панель модератора (очередь, объявление, флаги).

## Стек

- React 18 + React Router 6
- Vite 6
- Tailwind CSS 3
- Axios
- ESLint 9 (flat config) + Prettier 3, плюс проектный pre-commit hook
  автоматически чинит staged-файлы

## Раскладка

```
src/
  api/             -- axios-клиент + перехватчики (token, refresh)
  context/         -- AuthContext
  components/      -- общие компоненты (Navbar, ProtectedRoute)
  pages/           -- по одному файлу на маршрут
  index.css        -- entry Tailwind
  main.jsx         -- bootstrap приложения
  App.jsx          -- роутер
```

## Окружение

Dev-сервер проксирует `/api` на `http://localhost:8080` (gateway). Это
описано в [`vite.config.js`](./vite.config.js); чтобы направить SPA на
другой бэкенд, отредактируйте target прокси.

Production Nginx-образ (собирается через
[`Dockerfile`](./Dockerfile)) раздаёт статический бандл и
располагается за gateway в Compose-стеке.

## Локальный запуск

```bash
npm install
npm run dev      # Vite на http://localhost:3000, проксирует /api -> :8080
```

## Скрипты

| Скрипт                  | Назначение                                   |
|-------------------------|----------------------------------------------|
| `npm run dev`           | Dev-сервер Vite                              |
| `npm run build`         | Production-бандл в `dist/`                   |
| `npm run preview`       | Preview собранного бандла                    |
| `npm run lint`          | ESLint                                       |
| `npm run lint:fix`      | ESLint с auto-fix                            |
| `npm run format`        | Запись Prettier                              |
| `npm run format:check`  | Проверка Prettier (CI)                       |

## Зависимости

- **Обязательно:** доступный API Gateway по target прокси (по
  умолчанию `http://localhost:8080`).
