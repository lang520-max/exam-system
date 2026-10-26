#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Windows 侧的 SSH 助手（paramiko）—— 只因为 Windows 自带 OpenSSH 【无法非交互式传密码】：
ssh.exe 会弹交互提示，scp 同理，而本机没有 sshpass / plink。

用密钥时本来可以直接 ssh/scp；这个脚本的价值是：
  1. 密码登录也能自动化（密码从环境变量读，不进命令行、不进历史）
  2. 一次调用里既传文件又跑命令，退出码原样透传
  3. 非 root 登录时自动套 sudo：命令走 sudo -S（密码从 stdin 喂），
     文件先传到 /tmp 再 sudo mv 到位（SFTP 是以登录用户身份跑的，直接写 /opt 会 Permission denied）
  4. 输出统一按 UTF-8 解码并重设 stdout，避免 GBK 控制台把 ✓ / — 之类字符打崩

用法：
    set EXAM_SSH_PASSWORD=xxxx
    python ssh_win.py --host 1.2.3.4 --user root --cmd "uname -a"
    python ssh_win.py --host 1.2.3.4 --user ubuntu --key C:\\keys\\cloud.pem --script deploy/server/bootstrap.sh --sudo
    python ssh_win.py --host 1.2.3.4 --user root --put target/app.jar:/opt/app.jar --cmd "ls -l /opt/app.jar"
"""
import argparse
import os
import posixpath
import sys
import time

# 控制台可能是 GBK：不重设的话打印 ✓/— 会 UnicodeEncodeError
for _stream in ("stdout", "stderr"):
    try:
        getattr(sys, _stream).reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

try:
    import paramiko
except ImportError:
    sys.exit("缺少 paramiko：python -m pip install paramiko")


def shquote(s):
    return "'" + s.replace("'", "'\"'\"'") + "'"


def connect(args):
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    password = os.environ.get(args.password_env) if args.password_env else None
    try:
        if args.key:
            key = None
            key_pass = os.environ.get(args.key_pass_env) if args.key_pass_env else None
            for cls in (paramiko.Ed25519Key, paramiko.ECDSAKey, paramiko.RSAKey):
                try:
                    key = cls.from_private_key_file(args.key, password=key_pass)
                    break
                except Exception:
                    continue
            if key is None:
                sys.exit("读不了私钥：%s（.ppk 请先用 puttygen 转成 OpenSSH 格式）" % args.key)
            client.connect(args.host, port=args.port, username=args.user, pkey=key,
                           timeout=args.timeout, banner_timeout=args.timeout, auth_timeout=args.timeout)
        else:
            if not password:
                sys.exit("没给 --key，环境变量 %s 里也没有密码" % args.password_env)
            client.connect(args.host, port=args.port, username=args.user, password=password,
                           timeout=args.timeout, banner_timeout=args.timeout, auth_timeout=args.timeout)
    except paramiko.AuthenticationException:
        sys.exit("SSH 认证失败：用户名或密码不对（也可能是该实例禁止密码登录，只允许密钥）")
    except paramiko.SSHException as e:
        sys.exit("SSH 协议层失败：%s" % e)
    except OSError as e:
        sys.exit("连不上 %s:%s —— %s（安全组没放行 / 实例没运行 / IP 不对，都会长这样）"
                 % (args.host, args.port, e))
    return client, password


def run(client, command, sudo_password=None, timeout=1800, quiet=False):
    """执行命令并流式回显，返回远端退出码"""
    full = "sudo -S -p '' bash -lc %s" % shquote(command) if sudo_password else "bash -lc %s" % shquote(command)
    chan = client.get_transport().open_session()
    chan.settimeout(timeout)
    chan.exec_command(full)
    if sudo_password:
        chan.sendall(sudo_password + "\n")
        time.sleep(0.3)

    chunks = []
    while True:
        got = False
        while chan.recv_ready():
            data = chan.recv(65536)
            chunks.append(data)
            got = True
            if not quiet:
                sys.stdout.write(data.decode("utf-8", "replace"))
                sys.stdout.flush()
        if chan.exit_status_ready() and not chan.recv_ready():
            break
        if not got:
            time.sleep(0.05)
    while chan.recv_ready():
        data = chan.recv(65536)
        chunks.append(data)
        if not quiet:
            sys.stdout.write(data.decode("utf-8", "replace"))
    err = b""
    while chan.recv_stderr_ready():
        err += chan.recv_stderr(65536)
    if err:
        sys.stderr.write(err.decode("utf-8", "replace"))
        sys.stderr.flush()
    return chan.recv_exit_status(), b"".join(chunks).decode("utf-8", "replace")


def put(client, local, remote, sudo_password=None):
    if not os.path.isfile(local):
        sys.exit("本地文件不存在：%s" % local)
    size = os.path.getsize(local)
    sftp = client.open_sftp()
    try:
        if sudo_password:
            # SFTP 以登录用户身份运行，写 root 拥有的目录会失败 → 先落 /tmp 再 sudo 搬
            staging = "/tmp/exam-upload-%d-%s" % (int(time.time() * 1000), os.path.basename(remote))
            sftp.put(local, staging)
            run(client, "mkdir -p %s && mv -f %s %s && chmod 644 %s"
                % (shquote(posixpath.dirname(remote)), shquote(staging), shquote(remote), shquote(remote)),
                sudo_password=sudo_password, timeout=120, quiet=True)
        else:
            run(client, "mkdir -p %s" % shquote(posixpath.dirname(remote)), timeout=60, quiet=True)
            sftp.put(local, remote)
    finally:
        sftp.close()
    print("[上传] %s → %s (%.1f KB)" % (local, remote, size / 1024.0))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", required=True)
    ap.add_argument("--port", type=int, default=22)
    ap.add_argument("--user", default="root")
    ap.add_argument("--key", help="OpenSSH 私钥路径（推荐）")
    ap.add_argument("--key-pass-env", default="EXAM_SSH_KEY_PASS")
    ap.add_argument("--password-env", default="EXAM_SSH_PASSWORD", help="从这里读登录密码")
    ap.add_argument("--cmd", help="要执行的命令")
    ap.add_argument("--script", help="本地脚本路径（上传到远端 /tmp 再执行）")
    ap.add_argument("--put", action="append", default=[], metavar="LOCAL:REMOTE", help="上传文件，可重复")
    ap.add_argument("--sudo", action="store_true", help="用 sudo 执行（非 root 登录时）")
    ap.add_argument("--timeout", type=int, default=1800)
    args = ap.parse_args()

    client, password = connect(args)
    sudo_password = password if args.sudo else None
    try:
        for spec in args.put:
            if ":" not in spec:
                sys.exit("--put 需要 LOCAL:REMOTE 形式：%s" % spec)
            local, remote = spec.rsplit(":", 1)
            put(client, local, remote, sudo_password)

        code = 0
        if args.script:
            remote = "/tmp/%s.%d" % (os.path.basename(args.script), int(time.time()))
            sftp = client.open_sftp()
            sftp.put(args.script, remote)
            sftp.close()
            print("[执行] %s（远端副本 %s）" % (args.script, remote))
            code, _ = run(client, "bash %s" % shquote(remote), sudo_password=sudo_password, timeout=args.timeout)
        if args.cmd:
            print("[执行] %s" % args.cmd)
            c, _ = run(client, args.cmd, sudo_password=sudo_password, timeout=args.timeout)
            code = c or code
        sys.exit(code)
    finally:
        client.close()


if __name__ == "__main__":
    main()