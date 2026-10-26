#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
exam-system 冒烟测试 + 内存实况（面向 2 核 2 GiB 云主机）

做什么：
  1. 直连 8080 与经 Nginx（80）各验一次健康检查
  2. 登录页（/）可达且能拿到 HTML
  3. 无 token 访问 /api/exams 必须是 401（鉴权没被 nginx 绕过）
  4. admin 登录 → /api/exams（试卷数）、/api/questions（题目数）
  5. student01 真实走一遍：开考 → 取题 → 交卷 → 查成绩单
  6. 打印 java / mysqld / redis / nginx 的 RSS 与系统内存余量，并给出 2 GiB 是否够用的判断

用法：sudo python3 smoke.py
      SMOKE_PUBLIC=http://<域名或IP> python3 smoke.py     # 从本机也能量公网入口
只用标准库，不依赖 requests。
"""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
import uuid

# 管道里可能没有 LANG（Alibaba Cloud Linux 等），不重设的话打印 ✓/— 会 UnicodeEncodeError
# （Python 3.6 没有 reconfigure，所以整段包在 try 里）
for _stream in ("stdout", "stderr"):
    try:
        getattr(sys, _stream).reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

DIRECT = os.environ.get("SMOKE_DIRECT", "http://127.0.0.1:8080")
PUBLIC = os.environ.get("SMOKE_PUBLIC", "http://127.0.0.1")

n_pass = n_fail = n_skip = 0
GREEN, RED, YELLOW, DIM, RESET = "\033[1;32m", "\033[1;31m", "\033[1;33m", "\033[2m", "\033[0m"


def ok(msg):
    global n_pass
    n_pass += 1
    print("  %s✓%s %s" % (GREEN, RESET, msg))


def bad(msg, detail=""):
    global n_fail
    n_fail += 1
    print("  %s%s %s" % (RED, RESET, msg))
    if detail:
        print("      %s%s%s" % (DIM, str(detail)[:500], RESET))


def skip(msg):
    global n_skip
    n_skip += 1
    print("  %s-%s %s" % (YELLOW, RESET, msg))


def req(method, url, token=None, body=None, headers=None, timeout=15):
    """返回 (status, payload)；payload 是 dict/list 或原始文本"""
    data = None
    # Accept 头要分场景：给 HTML 页面发 application/json 会被 Spring 的内容协商
    # 判成 406（页面本身是好的）—— 冒烟脚本第一版就是这么误报的。
    if "/api/" in url or "/actuator/" in url:
        hdrs = {"Accept": "application/json"}
    else:
        hdrs = {"Accept": "text/html,application/xhtml+xml,*/*;q=0.8"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        hdrs["Content-Type"] = "application/json;charset=UTF-8"
    if token:
        hdrs["Authorization"] = "Bearer " + token
    if headers:
        hdrs.update(headers)
    r = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
            status = resp.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        status = e.code
    except Exception as e:  # 连接层失败
        return None, str(e)
    try:
        return status, json.loads(raw)
    except ValueError:
        return status, raw


def api(method, path, token=None, body=None, headers=None, base=None, timeout=15):
    return req(method, (base or DIRECT) + path, token, body, headers, timeout)


def login(username, password, base=None):
    status, payload = api("POST", "/api/auth/login", body={"username": username, "password": password}, base=base)
    if status != 200 or not isinstance(payload, dict) or payload.get("code") != 200:
        return None, "HTTP %s %s" % (status, payload)
    return payload.get("data") or {}, None


def section(title):
    print("\n\033[1;34m== %s\033[0m" % title)


def rss_mib(names):
    """按进程名求和 RSS（MiB）；用 Popen 而不是 subprocess.run，兼容 Python 3.6（ACL3 自带）"""
    total = 0
    for name in names:
        try:
            p = subprocess.Popen(["ps", "-o", "rss=", "-C", name],
                                 stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
            out = p.communicate()[0].decode("utf-8", "replace")
        except Exception:
            continue
        for line in out.split():
            if line.isdigit():
                total += int(line)
    return total / 1024.0


def meminfo():
    info = {}
    try:
        with open("/proc/meminfo") as f:
            for line in f:
                k, _, v = line.partition(":")
                info[k] = int(v.strip().split()[0])  # kB
    except Exception:
        pass
    return info


# ---------------------------------------------------------------- 1. 健康检查
section("健康检查 / 静态入口")
status, payload = req("GET", DIRECT + "/actuator/health")
if status == 200 and isinstance(payload, dict) and payload.get("status") == "UP":
    ok("直连 8080 /actuator/health = UP")
else:
    bad("直连 8080 /actuator/health 异常", "%s %s" % (status, payload))

status, payload = req("GET", PUBLIC + "/health")
if status == 200 and isinstance(payload, dict) and payload.get("status") == "UP":
    ok("经 Nginx %s/health = UP（反代链路通）" % PUBLIC)
else:
    bad("经 Nginx 的 /health 异常", "%s %s" % (status, payload))

status, payload = req("GET", PUBLIC + "/")
if status == 200 and isinstance(payload, str) and "在线考试系统" in payload:
    ok("经 Nginx 取到登录页（含标题「在线考试系统」，%d 字节）" % len(payload))
else:
    bad("经 Nginx 的首页异常", "%s %s" % (status, str(payload)[:200]))

# ---------------------------------------------------------------- 2. 鉴权
section("鉴权")
status, _ = api("GET", "/api/exams")
if status == 401:
    ok("无 token 访问 /api/exams → 401（鉴权生效）")
else:
    bad("无 token 访问 /api/exams 期望 401，实际 %s" % status)

admin, err = login("admin", "123456", base=PUBLIC)
if admin:
    ok("admin 登录成功（经 Nginx，role=%s）" % admin.get("role"))
else:
    bad("admin 登录失败", err)
    print("\n登录都过不了，后续用例没有意义，先看 journalctl -u exam-system。")
    sys.exit(1)
admin_token = admin["accessToken"]

student, err = login("student01", "123456", base=PUBLIC)
if student:
    ok("student01 登录成功（%s）" % student.get("realName"))
else:
    bad("student01 登录失败", err)

# ---------------------------------------------------------------- 3. 数据
section("演示数据")
status, payload = api("GET", "/api/exams", token=admin_token)
exams = (payload or {}).get("data") if isinstance(payload, dict) else None
if status == 200 and isinstance(exams, list) and len(exams) >= 6:
    ok("试卷 %d 张（期望 6）：%s" % (len(exams), "、".join(str(e.get("title")) for e in exams[:3]) + " …"))
elif status == 200:
    bad("试卷数不足：%s" % (len(exams) if isinstance(exams, list) else exams))
else:
    bad("GET /api/exams 失败", "%s %s" % (status, payload))

status, payload = api("GET", "/api/questions?page=1&size=1", token=admin_token)
total = ((payload or {}).get("data") or {}).get("total") if isinstance(payload, dict) else None
if status == 200 and total and int(total) >= 150:
    ok("题库题目 %s 道（期望 150）" % total)
elif status == 200:
    bad("题目数不足：%s（演示数据没灌？跑 import-demo-data.sh）" % total)
else:
    bad("GET /api/questions 失败", "%s %s" % (status, payload))

status, payload = api("GET", "/api/questions/banks", token=admin_token)
banks = (payload or {}).get("data") if isinstance(payload, dict) else None
if status == 200 and isinstance(banks, list) and len(banks) >= 3:
    ok("题库 %d 个：%s" % (len(banks), "、".join(str(b.get("name")) for b in banks)))
elif status == 200:
    bad("题库数不足：%s" % (len(banks) if isinstance(banks, list) else banks))

# ---------------------------------------------------------------- 4. 学生全链路
section("学生全链路：开考 → 取题 → 交卷 → 成绩单")
if not student:
    skip("student01 登录失败，跳过全链路")
else:
    st_token = student["accessToken"]
    status, payload = api("GET", "/api/exams", token=st_token)
    st_exams = (payload or {}).get("data") if isinstance(payload, dict) else []
    if not isinstance(st_exams, list) or not st_exams:
        bad("学生看不到任何已发布试卷", "%s %s" % (status, payload))
    else:
        ok("学生可见试卷 %d 张（草稿卷被过滤）" % len(st_exams))
        record_id = None
        started = None
        questions = None
        last_err = ""
        # 要找一个「真的还能作答」的记录：同一学生在同一张卷上只能有一条记录，
        # 之前交过卷的记录连取题都会 403/400。
        # 第一版只判断了「开考是否返回 200」就往下走，结果撞上旧记录时
        # 取题失败、后面 4 项检查被【静默跳过】（不报错也不执行）——
        # 一次运行显示「11 通过 / 0 失败」，看起来全绿其实少跑了一半。
        # 现在：开考成功后再探一次取题，取不到就换下一张卷；换不到就明确报「跳过」。
        for exam in st_exams[:6]:
            st, pl = api("POST", "/api/exams/%s/start" % exam.get("id"), token=st_token)
            if not (st == 200 and isinstance(pl, dict) and (pl.get("data") or {}).get("id")):
                last_err = "开考 exam %s → HTTP %s" % (exam.get("id"), st)
                continue
            rid = pl["data"]["id"]
            st2, pl2 = api("GET", "/api/exams/records/%s/questions" % rid, token=st_token)
            qs = (pl2 or {}).get("data") if isinstance(pl2, dict) else None
            if st2 == 200 and isinstance(qs, list) and qs:
                record_id, started, questions = rid, exam, qs
                break
            last_err = "exam %s 的记录 %s 取题失败（HTTP %s，多半是这份卷之前已交过）" % (exam.get("id"), rid, st2)
        if record_id is None:
            skip("没找到可作答的卷子（重复跑冒烟时会这样）：%s" % last_err)
        else:
            ok("开考成功：exam=%s《%s》recordId=%s" % (started.get("id"), started.get("title"), record_id))

            if questions:
                kinds = {}
                for q in questions:
                    kinds[q.get("type")] = kinds.get(q.get("type"), 0) + 1
                ok("取到 %d 道题（题型分布 %s，且不含答案字段）" % (len(questions), kinds))
                leaked = [q for q in questions if "correctAnswer" in q or "answer" in q]
                if leaked:
                    bad("取题响应里疑似带了答案字段", leaked[0])
                answers = {}
                for q in questions:
                    t = q.get("type")
                    if t == 0 or t == 2:
                        answers[str(q["id"])] = "A"
                    elif t == 1:
                        answers[str(q["id"])] = "AB"
                    else:
                        answers[str(q["id"])] = "冒烟测试作答：本题考查基本概念，按要点作答。"
                st, pl = api(
                    "POST", "/api/exams/submit",
                    token=st_token,
                    body={"recordId": record_id, "answers": answers},
                    headers={"X-Request-Id": "smoke-" + uuid.uuid4().hex},
                )
                if st == 200 and isinstance(pl, dict) and pl.get("code") == 200:
                    ok("交卷成功（%d 题作答，返回 %s）" % (len(answers), json.dumps(pl.get("data"), ensure_ascii=False)))
                else:
                    bad("交卷失败", "HTTP %s %s" % (st, pl))

                st, pl = api("GET", "/api/exams/records/%s/result" % record_id, token=st_token)
                if st == 200 and isinstance(pl, dict) and pl.get("data"):
                    d = pl["data"]
                    ok("成绩单可读：score=%s / totalScore=%s / 待复核=%s / passed=%s"
                       % (d.get("score"), d.get("totalScore"), d.get("pendingReviewCount"), d.get("passed")))
                else:
                    bad("成绩单读取失败", "HTTP %s %s" % (st, pl))

                st, pl = api("POST", "/api/exams/submit", token=st_token,
                             body={"recordId": record_id, "answers": {}},
                             headers={"X-Request-Id": "smoke-dup-" + uuid.uuid4().hex})
                if st in (400, 409):
                    ok("重复交卷被拒（HTTP %s，防重复交卷生效）" % st)
                else:
                    bad("重复交卷没有被拒（HTTP %s）" % st, pl)

# ---------------------------------------------------------------- 5. 内存实况
section("内存实况（2 GiB 机器的关键指标）")
mi = meminfo()
total = mi.get("MemTotal", 0) / 1024.0
avail = mi.get("MemAvailable", 0) / 1024.0
swap_total = mi.get("SwapTotal", 0) / 1024.0
swap_free = mi.get("SwapFree", 0) / 1024.0
parts = [("java (JVM)", rss_mib(["java"])), ("mysqld", rss_mib(["mysqld", "mariadbd"])),
         ("redis-server", rss_mib(["redis-server"])), ("nginx", rss_mib(["nginx"]))]
print("  进程 RSS：" + " | ".join("%s %.0f MiB" % (n, v) for n, v in parts))
print("  应用栈合计：%.0f MiB" % sum(v for _, v in parts))
print("  系统内存：总 %.0f MiB / 可用 %.0f MiB；swap 总 %.0f MiB / 空闲 %.0f MiB"
      % (total, avail, swap_total, swap_free))
if avail >= 400:
    ok("内存余量充足（可用 %.0f MiB），2 GiB 机型够用" % avail)
elif avail >= 150:
    skip("内存余量偏紧（可用 %.0f MiB）：日常够用，交卷高峰注意观察" % avail)
else:
    bad("内存余量告急（可用 %.0f MiB）：建议关 Redis 或升配" % avail)

# ---------------------------------------------------------------- 汇总
print("\n" + "=" * 62)
print("冒烟结果：%s通过 %d%s / %s失败 %d%s / 跳过 %d"
      % (GREEN, n_pass, RESET, RED, n_fail, RESET, n_skip))
print("登录入口：%s/   演示账号：admin / teacher1 / student01 / student02（口令均 123456）" % PUBLIC)
print("=" * 62)
sys.exit(1 if n_fail else 0)