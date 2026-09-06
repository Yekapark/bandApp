#!/bin/sh
# 인증서가 갱신돼도 nginx 는 그걸 모른다 — 파일을 기동할 때 한 번만 읽는다.
# 6시간마다 reload 해서 갱신분을 집어 들게 한다. reload 는 접속이 끊기지 않는다.
#
# 이 루프를 compose 의 `command:` 로 넣었더니 설정이 통째로 생성되지 않는 사고가 났다.
# nginx 공식 이미지의 시작 스크립트는 **명령의 첫 낱말이 nginx 일 때만**
# /docker-entrypoint.d/*.sh 를 돌린다(템플릿 치환이 그중 하나다). command 를
# `sh -c ...` 로 바꾸는 순간 app.conf 가 만들어지지 않아 기본 페이지만 뜬다.
# 그래서 command 는 건드리지 않고, 이미지가 제공하는 확장 지점에 얹는다.
( while :; do sleep 6h; nginx -s reload 2>/dev/null || true; done ) &
