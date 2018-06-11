#!/usr/bin/env python
import argparse
import hcl
import fileinput
import sys
import logging
from terraform_utils.modules_utils import *

parser = argparse.ArgumentParser(description='Tag git repos of terraform modules with environment and release number')
parser.add_argument('--build', help='Build number')
parser.add_argument('-D', '--debug', help='Enables debug mode for extra information', action="store_true")

args = parser.parse_args()
build = args.build
debug = args.debug
log_level = logging.INFO

if debug:
    log_level = logging.DEBUG

logging.basicConfig(stream=sys.stderr, level=log_level)


def replace_all(file, search_exp, replace_exp):
    for line in fileinput.input(file, inplace=1):
        if search_exp in line:
            line = line.replace(search_exp, replace_exp)
        sys.stdout.write(line)


def update_source_version(terraform_module_file, version):
    source_path, new_source_path = None, None
    with open(terraform_module_file, "r+") as fp:
        modules_tf = hcl.load(fp)
        for source in modules_tf["module"].values():
            source_path = source["source"]
            new_source_path = '{}?ref={}'.format(source_path.split('?', 1)[0], version)

    logging.info("Replacing module source from {} to {}".format(source_path, new_source_path))
    replace_all(terraform_module_file, source_path, new_source_path)

    return True


working_dir = os.getcwd()

logging.info("Checking if modules.tf exists at {}".format(working_dir))

module_file = has_module_file(working_dir)
updated = update_source_version(module_file, build)
logging.info("Successfully update module source") if updated else logging.info("Failed to update")
