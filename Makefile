# switchapk - host build (tests) and helpers. The Nintendo Switch build lives in Makefile.switch.

CC ?= cc
BUILD ?= build/host
CFLAGS ?= -O2 -g
CFLAGS += -std=gnu11 -Wall -Wextra -Wno-unused-parameter -Wno-missing-field-initializers -Isrc -Ithird_party \
          -fno-strict-aliasing -fwrapv -pthread
LDLIBS += -lz -lm -lpthread -ldl

SRCS := $(filter-out src/app/main_switch.c src/platform/platform_switch.c, \
          $(wildcard src/core/*.c src/vm/*.c src/native/*.c src/nativeloader/*.c src/android/*.c src/gfx/*.c \
                     src/platform/*.c src/app/*.c))
ASMS := $(wildcard src/vm/*.S src/nativeloader/*.S)
OBJS := $(SRCS:%.c=$(BUILD)/%.o) $(ASMS:%.S=$(BUILD)/%.o)
DEPS := $(OBJS:.o=.d)

all: $(BUILD)/switchapk-host build/java/framework.dex

$(BUILD)/switchapk-host: $(OBJS)
	$(CC) $(CFLAGS) -o $@ $^ $(LDLIBS)

$(BUILD)/%.o: %.c
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -MMD -MP -c -o $@ $<

$(BUILD)/%.o: %.S
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -c -o $@ $<

build/java/framework.dex: $(shell find java -name '*.java') tools/build_java.sh
	./tools/build_java.sh

java: build/java/framework.dex

test: all
	./tests/run_tests.sh

clean:
	rm -rf build/host build/java

.PHONY: all java test clean
-include $(DEPS)
