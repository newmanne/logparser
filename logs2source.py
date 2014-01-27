import re
from optparse import OptionParser

if __name__ == "__main__":

	parser = OptionParser()
	parser.add_option("-l", "--logfile", help="input log file name", metavar="FILE")
	parser.add_option("-rf", "--regexfile", help="regex file name", metavar="FILE")

	(options, args) = parser.parse_args()

	regexes = []
	with open(options.regexfile) as regex_file:
		for line in regex_file.readlines():
			regexes.append(re.compile(line))

	with open(options.logfile) as logfile:
		for line in logfile.readlines():
			for regex in regexes:
				if regex.match(line):
					print line, "matches", regex