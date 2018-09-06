#!/usr/bin/env python
import argparse
import hcl
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


def replace_all(file_path, search_exp, replace_exp):
    with open(file_path, 'r') as f:
        contents = f.read()

    contents = contents.replace(search_exp, replace_exp)
    with open(file_path, 'w') as f:
        f.write(contents)


def update_source_version(tf_modules_file, version):
    source_path = None
    with open(tf_modules_file, "r+") as fp:
        modules_tf = hcl.load(fp)
        for source in modules_tf["module"].values():
            source_path = source["source"]

    new_source_path = '{}?ref={}'.format(source_path.split('?', 1)[0], version)
    logging.info("Replacing module source from {} to {}".format(source_path, new_source_path))
    replace_all(tf_modules_file, source_path, new_source_path)

    return True


def update_artifact_version(terraform_vars_file, version):
    current_version = None
    with open(terraform_vars_file, "r+") as fp:
        vars_tf = hcl.load(fp)
        for key, value in vars_tf["variable"].iteritems():
            if 'artifact_version' in key:
                current_version = value['default']
    logging.info("Replacing artifact version from {} to {}".format(current_version, version))
    replace_all(terraform_vars_file, current_version, version)

    return True


working_dir = os.getcwd()

logging.info("Checking if modules.tf nad vars.tf exist at {}".format(working_dir))

module_file = has_module_file(working_dir)
vars_file = has_vars_file(working_dir)


updated = update_source_version(module_file, build) and update_artifact_version(vars_file, build)
logging.info("Successfully updated module source and artifact version") if updated else logging.info("Failed to update")
