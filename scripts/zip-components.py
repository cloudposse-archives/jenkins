#!/usr/bin/env python
import argparse
import json
import logging
import os
import shutil
import subprocess
import sys
from multiprocessing.pool import ThreadPool

parser = argparse.ArgumentParser(description='Update all components which are different to the current deployed')
parser.add_argument('-l', '--lambda-name', help='Name of a single lambda to zip (optional)')
parser.add_argument('-c', '--components-path', help='Path to location of components (defaults to current directory)',
                    default=os.getcwd())
parser.add_argument('-z', '--zip-path',
                    help='Location of where to put the zip files (defaults to terraform/lambda_files/')
parser.add_argument('-D', '--debug', help='Extra information about whats happening', action="store_true")
args = parser.parse_args()
lambda_to_zip = args.lambda_name
debug = args.debug
components_path = args.components_path
zip_path = args.zip_path
log_level = logging.INFO

if debug:
    log_level = logging.DEBUG

logging.basicConfig(stream=sys.stderr, level=log_level)


def get_zip_dir():
    if zip_path:
        zip_dir = zip_path
    else:
        zip_dir = "{}/terraform/lambda_files".format(os.getcwd())
    if not os.path.isdir(zip_dir):
        os.mkdir(zip_dir)
    return zip_dir


def get_services_info_json(module_path):
    services_info_path = "{}/services-info.json".format(module_path)
    logging.info("Checking if services-info.json exists at {}".format(services_info_path))
    if not os.path.isfile(services_info_path):
        logging.info("services-info.json not found")
        return dict()
    with open(services_info_path, 'r') as sj:
        return json.load(sj)


def get_components_to_zip(services_info_json):
    components_to_zip = []
    if "components" not in services_info_json:
        logging.info("No components found in services-info.json: {}".format(services_info_json))
    else:
        for name, component in services_info_json["components"].iteritems():
            if "deployment" in component and component["deployment"] == "lambda":
                components_to_zip.append(name)
    if lambda_to_zip:
        if lambda_to_zip not in components_to_zip:
            exit("Lambda {} not found in services-info.json".format(lambda_to_zip))
        return [lambda_to_zip]
    else:
        return components_to_zip


def run_zip_command(component):
    try:
        logging.info("Zipping {}".format(component))
        component_path = "{}/{}".format(module_working_dir, component)
        process_output = subprocess.check_output(["zip-python-terraform", component_path])
        logging.debug(process_output)
        zip_file = None
        for line in process_output.split("\n"):
            if "ZIP_LOCATION" in line:
                zip_file = line.split("=")[1]
        if not zip_file:
            return "Failed to find zip location in output {}".format(process_output)
        target_zip = "{}/{}.zip".format(zip_directory, component)

        shutil.copy(zip_file, target_zip)
    except Exception as e:
        logging.error("Failed to zip for service {}".format(component), exc_info=True)
        return "Failed to zip for service {}".format(component, e.message)


module_working_dir = components_path
zip_directory = get_zip_dir()
services_info = get_services_info_json(module_working_dir)
to_zip = get_components_to_zip(services_info)

pool = ThreadPool(10)
results = pool.map_async(run_zip_command, to_zip)
pool.close()
pool.join()

for failed in results.get():
    if failed:
        logging.error(failed)
