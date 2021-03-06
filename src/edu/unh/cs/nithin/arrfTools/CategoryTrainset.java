/**
 * @Author: Nithin
 * @Date:   2019-04-15T15:58:53-04:00
 * @Last modified by:   Nithin
 * @Last modified time: 2019-11-27T21:11:41-05:00
 */
package edu.unh.cs.nithin.arrfTools;

import java.io.File;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import edu.unh.cs.treccar_v2.Data;
import edu.unh.cs.treccar_v2.Data.Section;
import edu.unh.cs.treccar_v2.Data.Page;
import edu.unh.cs.treccar_v2.Data.Page.SectionPathParagraphs;
import edu.unh.cs.treccar_v2.read_data.DeserializeData;

public class CategoryTrainset {

	public CategoryTrainset() throws FileNotFoundException {

	}

	/**
	 * Loop through all pages. Find list of categories associated with each page.
	 * For each category add the corresponding page name in Hashmap. Add list of
	 * pages to each categories the page falls under. Return Hashmap
	 * @param trainSetFilePath
	 * @return
	 * @throws FileNotFoundException
	 */
	public Map<String, ArrayList<Data.Page>> getCategoryPageMap(String trainSetFilePath) throws FileNotFoundException {
		FileInputStream fileInputStream = new FileInputStream(new File(trainSetFilePath));
		Map<String, ArrayList<Data.Page>> categoryPagesMap = new HashMap<>();
		ArrayList<Data.Page> pageList;
		int pageCount = 0;
		for (Data.Page page : DeserializeData.iterableAnnotations(fileInputStream)) {
			pageCount++;
			ArrayList<String> catList = page.getPageMetadata().getCategoryNames();
			for (String category : catList) {
				category = category.replaceAll("[\\s\\:/]", "_");
				pageList = categoryPagesMap.get(category);
				if (pageList == null) {
					ArrayList<Data.Page> newPageList = new ArrayList<Data.Page>();
					newPageList.add(page);
					categoryPagesMap.put(category, newPageList);
				} else {
					pageList.add(page);
					categoryPagesMap.put(category, pageList);
				}
			}
			System.out.println(pageCount);
			if (pageCount == 100000) {
				break;
			}
		}
		return categoryPagesMap;
	}

	/**
	 * Loop through all pages. For each page add the heading name and paragraph to
	 * Hashmap.
	 *
	 * @param pageNames
	 * @return mapParaHeading
	 */
	public Map<String, String> getHeadingParaMap(ArrayList<Page> pageNames) {
		String Heading = "";
		Map<String, String> mapParaHeading = new HashMap<>();
		for (Page page : pageNames) {
			String pageHeading = page.getPageId();
			Heading = pageHeading; // Heading will be page heading at the start of the page
			for (SectionPathParagraphs sectionPathParagraph : page.flatSectionPathsParagraphs()) {
				Iterator<Section> sectionPathIter = sectionPathParagraph.getSectionPath().iterator();
				// check for subheading
				while (sectionPathIter.hasNext()) {
					Section section = sectionPathIter.next();
					String sectionHeading = pageHeading + "/" + section.getHeadingId();
					if (sectionPathIter.hasNext()) {
						Section nextSection = sectionPathIter.next();
						Heading = sectionHeading + "/" + nextSection.getHeadingId();
					} else {
						Heading = sectionHeading;
					}

				}
				String para = sectionPathParagraph.getParagraph().getTextOnly();
				if (mapParaHeading.get(Heading) == null) {
					mapParaHeading.put(Heading, para);
				} else {
					para = mapParaHeading.get(Heading) + " " + para;
					mapParaHeading.put(Heading, para);
				}

			}
		}
		return mapParaHeading;
	}
}
