#!/usr/bin/env python
import argparse
import logging
import errno
import os
import json

logger = logging.basicConfig(level=logging.DEBUG,
                             format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')

LOGGER = logging.getLogger(__name__)

parser = argparse.ArgumentParser(description="Create a release file")
parser.add_argument('--module', help='The module we are deploying')
parser.add_argument('--source-env', help='The environment we are deploying from')
parser.add_argument('--target-env', help='The environment we are deploying too')

args = parser.parse_args()
MODULE = args.module
SOURCE_ENV = args.source_env
TARGET_ENV = args.target_env


def copy_release_file(module, source_env, target_env):
    with open('m2a-releases/{}/{}.json'.format(source_env, module)) as source_file:
        data = json.load(source_file)
    create_release_file(target_env, module, data)


def create_release_file(target_env, module, content):
    path_to = "m2a-releases/{}/{}.json".format(target_env, module)
    if not os.path.exists(os.path.dirname(path_to)):
        try:
            os.makedirs(os.path.dirname(path_to))
        except OSError as exc:
            if exc.errno != errno.EEXIST:
                raise

    with open(path_to, "w+") as new_file:
        json.dump(content, new_file)


copy_release_file(MODULE, SOURCE_ENV, TARGET_ENV)
